import scala.util.Properties

name := "backup-requests"

organization in ThisBuild := "com.autoscout24"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

version in ThisBuild := "1.0." + Properties.envOrElse("TRAVIS_BUILD_NUMBER", "0-SNAPSHOT")

scalaVersion := "2.11.8"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings",
  "-Yno-adapted-args", "-Xmax-classfile-name", "130")

libraryDependencies ++= Seq(
  "org.mockito" % "mockito-all" % "1.10.19",
  "org.specs2" %% "specs2-core" % "3.8.4.1-scalaz-7.1" % "test",
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % "test",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.5" % "test",
  "net.codingwell" %% "scala-guice" % "4.0.1",
  "com.autoscout24" %% "eventpublisher24" % "131"
)

resolvers in ThisBuild ++= Seq(
  Classpaths.sbtPluginReleases,
  "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"
)
