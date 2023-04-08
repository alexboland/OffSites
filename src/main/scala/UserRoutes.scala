import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive1, Route}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json._
import doobie.implicits._
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

import scala.concurrent.Future
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.duration._
import akka.util.Timeout
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.duration._
import akka.util.Timeout
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import akka.actor.typed.scaladsl.AskPattern._

import scala.util.{Failure, Success}

object UserRoutes {

  // JSON protocols
  implicit val signInRequestFormat = jsonFormat2(SignInRequest)
  implicit val signInResponseFormat = jsonFormat1(UserManager.SignInResponse)
  implicit val signOutRequestFormat = jsonFormat1(SignOutRequest)
  implicit val signOutResponseFormat = jsonFormat1(UserManager.SignOutResponse)

  case class SignInRequest(username: String, password: String)

  case class SignOutRequest(username: String)

  val jwtSecret = "your_jwt_secret_here"
  val jwtAlgorithm = Algorithm.HMAC256(jwtSecret)



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
                      val now = new Date()
                      val exp = new Date(now.getTime + 3600000) // 1 hour in the future
                      val token = JWT.create()
                        .withClaim("user", request.username)
                        .withIssuedAt(now)
                        .withExpiresAt(exp)
                        .sign(jwtAlgorithm)
                      complete(JsObject("token" -> token.toJson))
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
