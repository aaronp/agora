import Dependencies.testDependencies
import sbt._
import Keys._

object Dependencies {
  //https://github.com/typesafehub/scala-logging
  val logging = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7")

  val cucumber = Seq("core", "jvm", "junit").map(suffix =>
    "info.cukes" % s"cucumber-$suffix" % "1.2.5" % "test") ++ Seq(
    "info.cukes" %% "cucumber-scala" % "1.2.5" % "test"
  )

  def testDependencies(confs: String = "test") = Seq(
    "org.scalactic" %% "scalactic" % "3.0.1" % confs,
    "org.scalatest" %% "scalatest" % "3.0.1" % (confs + "->*"),
    "junit" % "junit" % "4.12" % confs)


  val circe = Seq("core", "generic", "parser", "optics").map(name => "io.circe" %% s"circe-$name" % "0.7.0")

  val json: Seq[ModuleID] = circe

  val akkaHttp = List("", "-core", "-spray-json", "-jackson", "-testkit").map { suffix =>
    "com.typesafe.akka" %% s"akka-http$suffix" % "10.0.5"
  } :+ ("de.heikoseeberger" %% "akka-http-circe" % "1.14.0")

  val commonTestDeps = testDependencies("test")

  val commonDependencies: Seq[ModuleID] = logging ++ commonTestDeps
  val apiDependencies: Seq[ModuleID] = commonDependencies ++ commonTestDeps
  val jsonDependencies: Seq[ModuleID] = commonDependencies ++ json ++ commonTestDeps
  val uiDependencies: Seq[ModuleID] = commonDependencies ++ jsonDependencies
  val domainDependencies: Seq[ModuleID] = commonDependencies ++ commonTestDeps ++
    Seq("com.typesafe" % "config" % "1.3.1")
  val restDependencies: Seq[ModuleID] = commonDependencies ++ akkaHttp ++ testDependencies("test") ++ cucumber
}
