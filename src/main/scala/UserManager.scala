import UserRoutes.jwtAlgorithm
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.server.{AuthorizationFailedRejection, Directive1}
import akka.http.scaladsl.server.Directives.{optionalHeaderValueByName, provide, reject}
import com.auth0.jwt.JWT

object UserManager {
  sealed trait Command
  case class SignIn(user: String, replyTo: ActorRef[SignInResponse]) extends Command
  case class SignOut(user: String, replyTo: ActorRef[SignOutResponse]) extends Command

  sealed trait Response
  case class SignInResponse(status: String) extends Response
  case class SignOutResponse(status: String) extends Response

  def apply(): Behavior[Command] = userManager(Set.empty)

  def authenticate: Directive1[String] = {
    optionalHeaderValueByName("Authorization").flatMap {
      case Some(token) =>
        try {
          val verifier = JWT.require(jwtAlgorithm).build()
          val decodedJWT = verifier.verify(token)
          val username = decodedJWT.getClaim("user").asString()
          provide(username)
        } catch {
          case _: Exception => reject(AuthorizationFailedRejection)
        }
      case None => reject(AuthorizationFailedRejection)
    }
  }

  //currently not required, as JWTs are stateless, but just keeping this code around as food for thought
  private def userManager(sessions: Set[String]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case SignIn(user, replyTo) =>
          if (sessions.contains(user)) {
            replyTo ! SignInResponse("Failed: User already signed in.")
            userManager(sessions)
          } else {
            context.log.info(s"User '$user' signed in.")
            replyTo ! SignInResponse("Success: User signed in.")
            userManager(sessions + user)
          }

        case SignOut(user, replyTo) =>
          if (sessions.contains(user)) {
            context.log.info(s"User '$user' signed out.")
            replyTo ! SignOutResponse("Success: User signed out.")
            userManager(sessions - user)
          } else {
            replyTo ! SignOutResponse("Failed: User not signed in.")
            userManager(sessions)
          }
      }
    }
}