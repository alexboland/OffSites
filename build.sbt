name := "OffSites"

version := "0.1"

scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.0",
  "com.typesafe.akka" %% "akka-http" % "10.5.0",
  "com.typesafe.akka" %% "akka-stream" % "2.8.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.0",
  "com.typesafe.akka" %% "akka-persistence" % "2.8.0",
  "com.typesafe.akka" %% "akka-persistence-typed" % "2.8.0",
  "com.typesafe.akka" %% "akka-http-xml" % "10.5.0",
  "org.tpolecat" %% "doobie-core" % "0.13.4",
  "org.tpolecat" %% "doobie-hikari" % "0.13.4",
  "org.tpolecat" %% "doobie-postgres" % "0.13.4",
  "org.typelevel" %% "cats-effect" % "2.5.1",
  "software.amazon.awssdk" % "s3" % "2.20.26",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.postgresql" % "postgresql" % "42.3.3",
  "org.mindrot" % "jbcrypt" % "0.4",
  "com.auth0" % "java-jwt" % "3.18.1",
  "io.circe" %% "circe-core" % "0.14.1",
  "io.circe" %% "circe-generic" % "0.14.1",
  "io.circe" %% "circe-parser" % "0.14.1",
  "com.auth0" % "java-jwt" % "3.18.1",
  "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "5.0.0",
)

unmanagedResourceDirectories in Compile += baseDirectory.value / "frontend/public"
