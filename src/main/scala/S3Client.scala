import java.nio.file.Path
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse, PutObjectRequest, PutObjectResponse}

import java.io.File
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.io.Source.fromFile

object S3Client {

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

  val credentials = AwsBasicCredentials.create(accessKey, secretKey)
  val s3Client = S3AsyncClient.builder()
    .credentialsProvider(StaticCredentialsProvider.create(credentials))
    .region(Region.of(region))
    .build()

  def uploadImage(imageKey: String, filePath: Path): Future[PutObjectResponse] = {
    val request = PutObjectRequest.builder()
      .bucket(bucketName)
      .key(imageKey)
      .build()

    s3Client.putObject(request, AsyncRequestBody.fromFile(filePath.toFile)).toScala
  }

  def downloadImage(imageKey: String, filePath: Path): Future[GetObjectResponse] = {
    val request = GetObjectRequest.builder()
      .bucket(bucketName)
      .key(imageKey)
      .build()

    val transformer: AsyncResponseTransformer[GetObjectResponse, GetObjectResponse] =
      AsyncResponseTransformer.toFile[GetObjectResponse](filePath.toFile)

    s3Client.getObject(request, transformer).toScala
  }
}
