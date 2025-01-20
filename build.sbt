// filepath: /Users/amarjeetmohanty/Developer/play_auth/build.sbt
name := """play_auth"""
organization := "scala-demo"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.16"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-slick" % "5.3.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.3.0",
  "mysql" % "mysql-connector-java" % "8.0.26",
  "org.mindrot" % "jbcrypt" % "0.4",
  "com.pauldijou" %% "jwt-play" % "5.0.0"
)
