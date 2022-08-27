ThisBuild / organization := "com.example"
ThisBuild / scalaVersion := "2.13.4"

lazy val root = (project in file(".")).settings(
  name := "ce3.g8",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-effect" % "3.0.0-RC2",
    "co.fs2" %% "fs2-io" % "3.0.0-M9",
    "org.apache.activemq" % "activemq-client" % "5.14.3"
  )
)
