import Database.cs
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, Multipart, StatusCodes}
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Route}
import akka.stream.scaladsl.{FileIO, Framing}

import java.nio.file.{Path, Paths}
import doobie.implicits._
import cats.effect.IO

object Routes {
  def protectedRoute(method: HttpMethod)(route: Route): Route = {
    UserManager.authenticate { authUsername =>
      method match {
        case HttpMethods.GET =>
          parameter("username") { formUsername =>
            if (authUsername == formUsername) {
              route
            } else {
              reject(AuthorizationFailedRejection)
            }
          }
        case HttpMethods.POST | HttpMethods.PUT | HttpMethods.DELETE | HttpMethods.PATCH =>
          formField("username") { formUsername =>
            if (authUsername == formUsername) {
              route
            } else {
              reject(AuthorizationFailedRejection)
            }
          }
      }
    }
  }

  def createUserRoute =
    path("users/create") {
      post {
        formFields("username", "password") { (username, password) =>
          Database.createUser(username, password).transact(Database.transactor).unsafeRunSync()
          complete("done.")
        }
      }
    }


  def createStageRoute: Route =
    protectedRoute(HttpMethods.POST) {
      formFields("user_id", "site_name") { (userId, siteName) =>
        Database.createStage(userId, siteName).transact(Database.transactor).unsafeRunSync()
        complete("done.")
      }
    }

  def createSiteRoute: Route =
    protectedRoute(HttpMethods.POST) {
      formFields("stage_id", "site_name") { (stageId, siteName) =>
        extractRequestContext { ctx =>
          implicit val materializer = ctx.materializer
          implicit val ec = ctx.executionContext

          val tempFilePath: Path = Paths.get("tmp/upload.tmp")
          val imageKey = java.util.UUID.randomUUID()

          fileUpload("image") {
            case (metadata, byteSource) =>
              val uploadFuture = byteSource.runWith(FileIO.toPath(tempFilePath)).flatMap { _ =>
                S3Client.uploadImage(imageKey.toString(), tempFilePath).map { _ =>
                  val imageUrl = s"https://${S3Client.bucketName}.s3.amazonaws.com/$imageKey"
                  Database.insertImageUrl(imageKey, imageUrl)
                    .flatMap({ _ => Database.createSite(stageId, siteName) })
                    .transact(Database.transactor).runAsync {
                    case Left(error) =>
                      IO {
                        println(s"An error occurred: $error")
                      }
                    case Right(_) =>
                      IO {
                        println("The IO completed successfully.")
                      }
                  }
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