import Domain.{Site, Stage, User}
import cats.effect.{Blocker, ContextShift, IO}
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor

import java.util.UUID
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import org.mindrot.jbcrypt.BCrypt

object Database {
  val dbDriver = "org.postgresql.Driver"
  val dbUrl = "jdbc:postgresql://localhost:5432/scala_bucket_test"
  val dbUser = "alexanderboland"
  val dbPassword = "password"

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val transactor: Transactor[IO] = {
    val connectEC = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(32))
    val blocker = Blocker.liftExecutionContext(connectEC)
    Transactor.fromDriverManager[IO](
      driver = dbDriver,
      url = dbUrl,
      user = dbUser,
      pass = dbPassword,
      blocker = blocker
    )
  }

  def createUser(username: String, password: String): ConnectionIO[Int] = {
    val salt = BCrypt.gensalt()
    val hashedPassword = BCrypt.hashpw(password, salt)
    // To validate the password later, just use BCrypt.checkpw(password, hashedPassword)
    sql"""INSERT INTO users (username, password) VALUES ($username, $hashedPassword)""".update.run
  }

  def getUser(username: String): ConnectionIO[Option[User]] = {
    sql"""SELECT (id, username, password) FROM users WHERE username= $username""".query[User].option
  }

  def createStage(user_id: String, name: String): ConnectionIO[Int] = {
    sql"""INSERT INTO images (image_key, url) VALUES ($user_id::uuid, $name)""".update.run
  }

  def createSite(stage_id: String, name: String): ConnectionIO[Int] = {
    sql"""INSERT INTO images (image_key, url) VALUES ($stage_id::uuid, $name)""".update.run
  }

  def createLink(site: Site, name: String): ConnectionIO[Int] = {
    sql"""INSERT INTO images (image_key, url) VALUES (${site.id}::uuid, $name)""".update.run
  }

  def insertImageUrl(imageKey: UUID, url: String): ConnectionIO[Int] = {
    sql"""INSERT INTO images (image_key, url) VALUES (${imageKey: java.util.UUID}::uuid, $url)""".update.run
  }

  def getImageUrl(imageKey: UUID): ConnectionIO[Option[String]] = {
    //add ::uuid back once i'm done testing this thing
    sql"""SELECT url FROM images WHERE image_key = ${imageKey.toString()}""".query[String].option
  }
}
