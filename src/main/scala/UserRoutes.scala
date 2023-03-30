import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json._
import doobie.implicits._


import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.duration._
import akka.util.Timeout
import org.mindrot.jbcrypt.BCrypt

import scala.util.{Failure, Success}

object UserRoutes {

  // JSON protocols
  implicit val signInRequestFormat = jsonFormat2(SignInRequest)
  implicit val signInResponseFormat = jsonFormat1(UserManager.SignInResponse)
  implicit val signOutRequestFormat = jsonFormat1(SignOutRequest)
  implicit val signOutResponseFormat = jsonFormat1(UserManager.SignOutResponse)

  case class SignInRequest(username: String, password: String)
  case class SignOutRequest(username: String)

  def apply(userManager: ActorRef[UserManager.Command])(implicit system: ActorSystem[_]): Route = {
    implicit val timeout: Timeout = 3.seconds

    concat(
      path("login") {
        post {
          entity(as[SignInRequest]) { request =>
            Database.getUser(request.username).map {
                case None => complete("user not found.")
                case Some(user) =>
                  if (!BCrypt.checkpw(user.password, request.password)) {
                    complete("invalid password.")
                  } else {
                    val signInResult: Future[UserManager.SignInResponse] =
                      userManager.ask(UserManager.SignIn(request.username, _))
                    onSuccess(signInResult) { response =>
                      complete(response.toString())
                    }
                  }
            }.transact(Database.transactor).unsafeRunSync()
          }
        }
      },
      path("logout") {
        post {
          entity(as[SignOutRequest]) { request =>
            val signOutResult: Future[UserManager.SignOutResponse] = userManager.ask(UserManager.SignOut(request.username, _))
            onSuccess(signOutResult) { response =>
              complete(response.toString())
            }
          }
        }
      }
    )
  }
}
