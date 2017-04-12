import sbt._

object Dependencies {
  //https://github.com/typesafehub/scala-logging
  val logging = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "ch.qos.logback" % "logback-classic" % "1.1.7")

  val cucumber = Seq("core", "jvm", "junit").map(suffix =>
    "info.cukes" % s"cucumber-$suffix" % "1.2.5" % "test") ++ Seq(
    "info.cukes" %% "cucumber-scala" % "1.2.5" % "test"
  )

  def testDependencies = Seq(
    "org.scalactic" %% "scalactic" % "3.0.1" % "test",
    "org.scalatest" %% "scalatest" % "3.0.1" % ("test->*"),
    "junit" % "junit" % "4.12" % "test")


  val circe = Seq("core", "generic", "parser", "optics").map(name => "io.circe" %% s"circe-$name" % "0.7.0")

  val json: Seq[ModuleID] = circe

  val akkaHttp = List("", "-core", "-testkit").map { suffix =>
    "com.typesafe.akka" %% s"akka-http$suffix" % "10.0.5"
  } :+ ("de.heikoseeberger" %% "akka-http-circe" % "1.14.0")

  val akka = List("","-testkit").map { suffix =>
    "com.typesafe.akka" %% s"akka$suffix" % "10.0.5"
  }

  val commonDependencies: Seq[ModuleID] = logging ++ testDependencies
  val apiDependencies: Seq[ModuleID] = commonDependencies ++ json
  val uiDependencies: Seq[ModuleID] = json
  val domainDependencies: Seq[ModuleID] = commonDependencies ++ Seq("com.typesafe" % "config" % "1.3.1") ++ akka
  val clientDependencies: Seq[ModuleID] = commonDependencies ++ akkaHttp
  val restDependencies: Seq[ModuleID] = clientDependencies ++ cucumber
}
