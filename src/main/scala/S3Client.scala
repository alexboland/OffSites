import java.nio.file.Path
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse, PutObjectRequest, PutObjectResponse}
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

object S3Client {
  val accessKey = "AKIAYML5UQ3CVHHSXTM5"
  val secretKey = "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"
  val region = "us-east-1"
  val bucketName = "aboland-testbucket"

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
