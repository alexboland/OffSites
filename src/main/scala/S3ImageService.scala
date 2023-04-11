import Routes._
import UserManager.Command
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpEntity.apply
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.alpakka.s3.{MemoryBufferType, S3Attributes, S3Headers, S3Settings}
import akka.stream.{ActorMaterializer, ClosedShape, IOResult, Materializer, SinkShape}
import akka.stream.scaladsl.{Broadcast, BroadcastHub, FileIO, Flow, GraphDSL, Keep, Sink, Source, StreamConverters, Zip}
import akka.util.ByteString
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`

import java.nio.file.Paths
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import doobie.implicits._
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import GraphDSL.Implicits._
import akka.NotUsed
import akka.http.scaladsl.client.RequestBuilding.WithTransformation

import scala.concurrent.duration.Duration
import java.io.File
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source.fromFile
import scala.io.StdIn


object S3ImageService {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Command] = ActorSystem(UserManager(), "S3ImageService")
    val userManager: ActorRef[UserManager.Command] = system.ref
    implicit val materializer: Materializer = Materializer(system)

    // Load environment variables from .env file
    val envFilePath = ".env"
    val envFile = new File(envFilePath)
    val envLines = fromFile(envFile).getLines().toList

    // Create map of environment variables
    val envVars = envLines.flatMap(line => line.split("=") match {
      case Array(key, value) => Some(key -> value)
      case _ => None
    }).toMap

    val accessKey = envVars.getOrElse("AWS_ACCESS_KEY", "")
    val secretKey = envVars.getOrElse("AWS_SECRET_KEY", "")
    val region = envVars.getOrElse("AWS_REGION", "")
    val bucketName = envVars.getOrElse("S3_BUCKET_NAME", "")

    // alpakka S3 settings
    val s3Settings = S3Settings()
      .withCredentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
      .withBufferType(MemoryBufferType)
      .withS3RegionProvider(new software.amazon.awssdk.regions.providers.AwsRegionProvider {
        override def getRegion: Region = Region.of(region)
      })

    val userRoutes = UserRoutes(userManager)(system)

    val routes = concat(userRoutes, createUserRoute)

    val route: Route =
      concat(
        path("upload") {
          (post & extractRequest & extractLog) { (request, log) =>
            fileUpload("file") {
              case (metadata, byteSource) =>
                val imageKey = java.util.UUID.randomUUID()

                val countBytesFlow: Flow[ByteString, ByteString, Future[Long]] = {
                  val countingFunc = (count: Long, bytes: ByteString) => Future.successful(count + bytes.size)
                  Flow[ByteString].foldAsync(0L)(countingFunc)
                    .mapMaterializedValue(_ => Future.successful(0L)).map { count => ByteString(count) }
                }

                val uploadPath = Paths.get("tmp/upload.tmp")

                def countBytesSink: Sink[ByteString, Future[Long]] = {
                  val countingFunc = (count: Long, bytes: ByteString) => Future.successful(count + bytes.size)

                  // Create a file sink to write the ByteString
                  val fileSink = FileIO.toPath(uploadPath)

                  // Create a counting sink to count the bytes
                  val countingSink = Sink.foldAsync[Long, ByteString](0L)(countingFunc)

                  // Combine the two sinks using alsoTo and mapMaterializedValue
                  Sink.fromGraph(GraphDSL.createGraph(countingSink) { implicit builder =>
                    counting =>
                      import GraphDSL.Implicits._

                      val broadcast = builder.add(Broadcast[ByteString](2))

                      // Connect the graph components
                      broadcast.out(0) ~> fileSink
                      broadcast.out(1) ~> counting

                      SinkShape(broadcast.in)
                  })
                }

                val writeFileGraph = byteSource
                  //.viaMat(countBytesFlow)(Keep.both)
                  //.toMat(FileIO.toPath(uploadPath))(Keep.both)
                  .toMat(countBytesSink)(Keep.both)

                val (ioResultFuture, byteCountFuture) = writeFileGraph.run()

                //val (byteCountFuture, ioResultFuture) = writeFileGraph.run()


                val uploaded = for {
                  contentLength <- byteCountFuture
                  //_ <- ioResultFuture
                  fileSource = FileIO.fromPath(uploadPath)
                  uploadResultSource = S3.putObject(bucketName, imageKey.toString, fileSource, contentLength,
                    metadata.contentType, S3Headers.empty).withAttributes(S3Attributes.settings(s3Settings))
                  uploadResult <- uploadResultSource.runWith(Sink.head)
                } yield uploadResult

                onComplete(uploaded) {
                  case Success(result) => complete(StatusCodes.Created, s"File uploaded to S3")
                  case Failure(exception) =>
                    print(s"FAIURE: ${exception.getMessage}")
                    complete(StatusCodes.InternalServerError, exception.getMessage)
                }
            }

            /*val tempFilePath = Paths.get("tmp/upload.tmp")
            val uploadedFileFuture = request.entity.dataBytes.runWith(FileIO.toPath(tempFilePath))

            onSuccess(uploadedFileFuture) { _ =>
              val imageKey = java.util.UUID.randomUUID()
              val uploadResult = S3Client.uploadImage(imageKey.toString(), tempFilePath)

              onSuccess(uploadResult) { _ =>
                val imageUrl = s"https://${S3Client.bucketName}.s3.amazonaws.com/$imageKey"
                Database.insertImageUrl(imageKey, imageUrl).transact(Database.transactor).unsafeRunSync()
                log.info(s"Image uploaded: $imageUrl")
                complete(imageUrl)
              }
            }*/
          }
        },
        path("download" / Segment) { imageKey =>
          get {
            Database.getImageUrl(UUID.fromString(imageKey)).transact(Database.transactor).unsafeRunSync() match {
              case Some(url) =>

                val s3Source: Source[ByteString, _] = S3.getObject(bucketName, imageKey).withAttributes(S3Attributes.settings(s3Settings)).map(_.data)

                val responseEntity = HttpEntity.Chunked.fromData(MediaTypes.`image/jpeg`, s3Source)

                complete(StatusCodes.OK, List(`Content-Type`(MediaTypes.`image/jpeg`)), responseEntity)

              // Old code
              /*val request = S3Client.downloadImage(imageKey, s3Settings)

              onComplete(request) {
                case Success(response) =>
                  val bytes = response.asByteArray()
                  complete(HttpEntity(`image/jpeg`, ByteString(bytes)))

                case Failure(exception) =>
                  complete(StatusCodes.InternalServerError, s"An error occurred: ${exception.getMessage}")
              }

              val file = new File("tmp/download.tmp")
              val fileSource: Source[ByteString, Any] = FileIO.fromPath(file.toPath)
              complete(HttpEntity(ContentTypes.`application/octet-stream`, file.length(), fileSource))*/
              case None => complete(StatusCodes.NotFound)
            }
          }
        }
      )

    val staticFilesRoute: Route =
      pathPrefix("public") {
        getFromResourceDirectory("public")
      } ~
        pathSingleSlash {
          get {
            getFromFile("frontend/public/testindex.html")
          }
        }


    val bindingFuture: Future[ServerBinding] = Http().newServerAt("localhost", 8080).bind(route ~ staticFilesRoute)
    //val bindingFuture: Future[ServerBinding] =
    //Http().newServerAt("localhost", 8080).bind(routes)

    println("Server online at http://localhost:8080/\nPress RETURN to stop...")

    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
