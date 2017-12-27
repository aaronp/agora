import sbt._

object Dependencies {

  val config = "com.typesafe" % "config" % "1.3.0"

  //https://github.com/typesafehub/scala-logging
  val logging =
    List("com.typesafe.scala-logging" %% "scala-logging" % "3.7.2", "ch.qos.logback" % "logback-classic" % "1.1.11")

  val cucumber = List("core", "jvm", "junit").map(suffix => "info.cukes" % s"cucumber-$suffix" % "1.2.5" % "test") :+ ("info.cukes" %% "cucumber-scala" % "1.2.5" % "test")

  val testDependencies = List(
    "org.scalactic" %% "scalactic" % "3.0.4" % "test",
    "org.scalatest" %% "scalatest" % "3.0.4" % "test",
    "org.pegdown"   % "pegdown"    % "1.6.0" % "test",
    "junit"         % "junit"      % "4.12"  % "test"
  )

  val akkaCirce = "de.heikoseeberger" %% "akka-http-circe" % "1.18.1"
  val circe     = List("core", "generic", "parser", "optics", "java8").map(name => "io.circe" %% s"circe-$name" % "0.8.0")

  val akkaHttp = List("", "-core").map { suffix =>
    "com.typesafe.akka" %% s"akka-http$suffix" % "10.0.10"
  } :+ akkaCirce :+ ("com.typesafe.akka" %% "akka-http-testkit" % "10.0.10" % "test")

  val pprint     = "com.lihaoyi"                  %% "pprint"              % "0.5.2"
  val streamsTck = "org.reactivestreams"          % "reactive-streams-tck" % "1.0.1" % "test"
  val swagger    = "com.github.swagger-akka-http" %% "swagger-akka-http"   % "0.11.0"
  val cors       = "ch.megard"                    %% "akka-http-cors"      % "0.2.2"

  val reactiveStreams = "org.reactivestreams" % "reactive-streams" % "1.0.1"

  val Config: List[ModuleID]  = config :: testDependencies
  val IO: List[ModuleID]      = logging ::: testDependencies
  val Api: List[ModuleID]     = reactiveStreams :: logging ::: testDependencies ::: circe
  val Rest: List[ModuleID]    = Api ::: akkaHttp ::: cucumber ::: List(streamsTck, pprint, swagger, cors)
  val RestApi: List[ModuleID] = Api ::: akkaHttp
}
