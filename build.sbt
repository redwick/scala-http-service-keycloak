import scala.collection.Seq

ThisBuild / version := "latest"

ThisBuild / scalaVersion := "2.13.15"

enablePlugins(JavaServerAppPackaging, DockerPlugin)

dockerExposedPorts := Seq(5055)
dockerRepository := Some("cr.selcloud.ru/marine-solutions")
dockerBaseImage := "registry.access.redhat.com/ubi9/openjdk-21:1.20-2.1721207866"

lazy val root = (project in file("."))
  .settings(
    name := "http-service"
  )

scalacOptions += "-Ymacro-annotations"


val PekkoVersion = "1.0.3"
val PekkoHttpVersion = "1.0.1"
val AkkaHttpCors = "1.2.0"
val SLF4JVersion = "2.0.13"
val SlickVersion = "3.5.1"
val PostgresSQLVersion = "42.7.3"
val CirceVersion = "0.14.9"

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion,
  "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
  "org.apache.pekko" %% "pekko-http" % PekkoHttpVersion,
  "org.apache.pekko" %% "pekko-slf4j" % PekkoVersion,
  "org.apache.pekko" %% "pekko-http-spray-json" % PekkoHttpVersion,
  "org.apache.pekko" %% "pekko-http-cors" % PekkoHttpVersion,
  "org.slf4j" % "slf4j-api" % SLF4JVersion,
  "org.slf4j" % "slf4j-simple" % SLF4JVersion,
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
)
