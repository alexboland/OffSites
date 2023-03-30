import Routes._
import UserManager.Command
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import java.nio.file.Paths
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import doobie.implicits._

import scala.concurrent.duration.Duration
import java.io.File
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn


object S3ImageService {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Command] = ActorSystem(UserManager(), "S3ImageService")
    val userManager: ActorRef[UserManager.Command] = system.ref
    implicit val materializer: Materializer = Materializer(system)

    val userRoutes = UserRoutes(userManager)(system)

    val routes = concat(userRoutes, createUserRoute)

    val route: Route =
      concat(
        path("upload") {
          (post & extractRequest & extractLog) { (request, log) =>
            val tempFilePath = Paths.get("tmp/upload.tmp")
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
            }
          }
        },
        path("download" / Segment) { imageKey =>
          get {
            Database.getImageUrl(UUID.fromString(imageKey)).transact(Database.transactor).unsafeRunSync() match {
              case Some(url) =>
                S3Client.downloadImage(imageKey, Paths.get("tmp/download.tmp"))
                val file = new File("tmp/download.tmp")
                val fileSource: Source[ByteString, Any] = FileIO.fromPath(file.toPath)
                complete(HttpEntity(ContentTypes.`application/octet-stream`, file.length(), fileSource))
              case None => complete(StatusCodes.NotFound)
            }
          }
        }
      )

    //val bindingFuture: Future[ServerBinding] = Http().newServerAt("localhost", 8080).bind(route)
    val bindingFuture: Future[ServerBinding] =
      Http().newServerAt("localhost", 8080).bind(routes)

    println("Server online at http://localhost:8080/\nPress RETURN to stop...")

    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
