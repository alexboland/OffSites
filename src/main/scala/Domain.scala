import java.util.UUID

object Domain {
  case class User(id: UUID, username: String, password: String)

  case class Stage(id: UUID, name: String)

  case class Site(id: UUID, stage: Stage, image: Image)

  case class Image(id: UUID)

  case class Link(id: UUID, origin: Site, destination: Site, x: Int, y: Int, width: Int, height: Int)
}