import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object UserManager {
  sealed trait Command
  case class SignIn(user: String, replyTo: ActorRef[SignInResponse]) extends Command
  case class SignOut(user: String, replyTo: ActorRef[SignOutResponse]) extends Command

  sealed trait Response
  case class SignInResponse(status: String) extends Response
  case class SignOutResponse(status: String) extends Response

  def apply(): Behavior[Command] = userManager(Set.empty)

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