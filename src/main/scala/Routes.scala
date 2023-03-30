import Database.cs
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Framing}

import java.nio.file.{Path, Paths}
import doobie.implicits._
import cats.effect.IO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import akka.stream.Materializer
import akka.util.ByteString
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

object Routes {

  /*def login(system: ActorSystem)(implicit materializer: Materializer) =
    path("users/login") {
      // Note: password is hashed
      parameters("username", "password") { (username, password) =>
        Database.getUser(username).map {
            case None => complete("user not found.")
            case Some(user) =>
              if (!BCrypt.checkpw(user.password, password)) {
                complete("invalid password.")
              } else {
                complete("aaa")
              }
        }
        //complete("done.")
      }
    }*/

  def createUserRoute(implicit materializer: Materializer) =
    path("users/create") {
      post {
        formFields("username", "password") { (username, password) =>
          Database.createStage(username, password).transact(Database.transactor).unsafeRunSync()
          complete("done.")
        }
      }
    }


  def createStageRoute(implicit materializer: Materializer) =
    path("stages/create") {
      post {
        formFields("user_id", "site_name") { (userId, siteName) =>
          Database.createStage(userId, siteName).transact(Database.transactor).unsafeRunSync()
          complete("done.")
        }
      }
    }

  def createSiteRoute(implicit materializer: Materializer) =
    path("sites/create") {
      post {
        extractRequestContext { ctx =>
          implicit val materializer = ctx.materializer
          implicit val ec = ctx.executionContext

          val tempFilePath: Path = Paths.get("tmp/upload.tmp")
          val imageKey = java.util.UUID.randomUUID()

          formFields("stage_id", "site_name") { (stageId, siteName) =>
            fileUpload("image") {
              case (metadata, byteSource) =>
                val uploadFuture = byteSource.runWith(FileIO.toPath(tempFilePath)).flatMap { _ =>
                  S3Client.uploadImage(imageKey.toString(), tempFilePath).map { _ =>
                    val imageUrl = s"https://${S3Client.bucketName}.s3.amazonaws.com/$imageKey"
                    Database.insertImageUrl(imageKey, imageUrl)
                      .flatMap({ _ => Database.createSite(stageId, siteName) })
                      .transact(Database.transactor).unsafeRunSync()
                  }
                }
                onSuccess(uploadFuture) { _ =>
                  complete("done.")
                }
            }
          }
        }
      }
    }
}