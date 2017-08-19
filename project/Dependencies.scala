import sbt._

object Dependencies {

  //https://github.com/typesafehub/scala-logging
  val logging = Seq("com.typesafe.scala-logging" %% "scala-logging" % "3.5.0", "ch.qos.logback" % "logback-classic" % "1.1.11")

  val cucumber = Seq("core", "jvm", "junit").map(suffix => "info.cukes" % s"cucumber-$suffix" % "1.2.5" % "test") :+ ("info.cukes" %% "cucumber-scala" % "1.2.5" % "test")

  val testDependencies = Seq(
    "org.scalactic" %% "scalactic" % "3.0.3" % "test",
    "org.scalatest" %% "scalatest" % "3.0.3" % ("test->*"),
    "org.pegdown"   % "pegdown"    % "1.6.0" % ("test->*"),
    "junit"         % "junit"      % "4.12"  % "test"
  )

  val circe = Seq("core", "generic", "parser", "optics").map(name => "io.circe" %% s"circe-$name" % "0.8.0")

  val akkaHttp = List("", "-core", "-testkit").map { suffix =>
    "com.typesafe.akka" %% s"akka-http$suffix" % "10.0.9"
  } :+ ("de.heikoseeberger" %% "akka-http-circe" % "1.17.0")

  val pprint      = "com.lihaoyi"                  %% "pprint"              % "0.5.2"
  val streamsTck  = "org.reactivestreams"          % "reactive-streams-tck" % "1.0.0" % "test"
  val betterFiles = "com.github.pathikrit"         %% "better-files"        % "2.17.1"
  val swagger     = "com.github.swagger-akka-http" %% "swagger-akka-http"   % "0.10.0"
  val cors        = "ch.megard"                    %% "akka-http-cors"      % "0.2.1"

  val Api  = logging ++ testDependencies ++ circe
  val Rest = Api ++ akkaHttp ++ cucumber ++ List(streamsTck, pprint, betterFiles, swagger, cors)
  val UI   = Api
}
