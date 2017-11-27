import scala.util.Properties

name := "backup-requests"

organization in ThisBuild := "com.autoscout24"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

version in ThisBuild := "1.0." + Properties.envOrElse("TRAVIS_BUILD_NUMBER", "0-SNAPSHOT")

crossScalaVersions in ThisBuild := Seq("2.12.4", "2.11.11")

scalaVersion := "2.12.4"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings",
  "-Yno-adapted-args", "-Xmax-classfile-name", "130")

libraryDependencies ++= Seq(
  "org.mockito" % "mockito-all" % "1.10.19",
  "org.specs2" %% "specs2-core" % "4.0.2-f59ba9b-20171124130950" % "test",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % "test",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
  "net.codingwell" %% "scala-guice" % "4.1.1",
  "com.typesafe.akka" %% "akka-actor" % "2.5.7"
)

resolvers in ThisBuild ++= Seq(
  Classpaths.sbtPluginReleases,
  "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"
)
