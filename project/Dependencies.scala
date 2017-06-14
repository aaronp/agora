import sbt._
import Keys._

object Dependencies {

  //https://github.com/typesafehub/scala-logging
  val logging = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" % "logback-classic" % "1.1.11")

  val cucumber = Seq("core", "jvm", "junit").map(suffix =>
    "info.cukes" % s"cucumber-$suffix" % "1.2.5" % "test") :+ ("info.cukes" %% "cucumber-scala" % "1.2.5" % "test")

  val testDependencies = Seq(
    "org.scalactic" %% "scalactic" % "3.0.3" % "test",
    "org.scalatest" %% "scalatest" % "3.0.3" % ("test->*"),
    "org.pegdown" % "pegdown" % "1.6.0" % ("test->*"),
    "junit" % "junit" % "4.12" % "test")

  val circe = Seq("core", "generic", "parser", "optics").map(name => "io.circe" %% s"circe-$name" % "0.8.0")

  val akkaHttp = List("", "-core", "-testkit").map { suffix =>
    "com.typesafe.akka" %% s"akka-http$suffix" % "10.0.5" //10.0.7
  } :+ ("de.heikoseeberger" %% "akka-http-circe" % "1.14.0")

  val pprint = "com.lihaoyi" %% "pprint" % "0.4.3"

  val streamsTck = "org.reactivestreams" % "reactive-streams-tck" % "1.0.0" % "test"

  val Api = logging ++ testDependencies ++ circe

  val Rest = Api ++ akkaHttp ++ List(streamsTck) ++ cucumber :+ pprint

  val UI = Api

}
