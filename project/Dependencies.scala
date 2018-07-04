import sbt._

object Dependencies {

  val config = "com.typesafe" % "config" % "1.3.0"

  //https://github.com/typesafehub/scala-logging
  val logging =
    List("com.typesafe.scala-logging" %% "scala-logging" % "3.7.2", "ch.qos.logback" % "logback-classic" % "1.1.11")

  val cucumber = {
    List(
//      "io.cucumber" %% "cucumber-scala" % "2.0.0-SNAPSHOT" % "test",
      "io.cucumber" %% "cucumber-scala" % "2.0.0" % "test",
      "io.cucumber" % "cucumber-junit" % "2.0.0" % "test"
    )
  }

  val testDependencies = List(
    "org.scalactic" %% "scalactic" % "3.0.4" % "test",
    "org.scalatest" %% "scalatest" % "3.0.4" % "test",
    "org.pegdown" % "pegdown" % "1.6.0" % "test",
    "junit" % "junit" % "4.12" % "test"
  )

  val akkaCirce = "de.heikoseeberger" %% "akka-http-circe" % "1.19.0"
  val circe: List[ModuleID] = List("core", "generic", "parser", "optics", "java8").map(name => "io.circe" %% s"circe-$name" % "0.9.1")

  val akkaHttp = List("", "-core").map { suffix =>
    "com.typesafe.akka" %% s"akka-http$suffix" % "10.0.10"
  } :+ akkaCirce :+ ("com.typesafe.akka" %% "akka-http-testkit" % "10.0.10" % "test")

  val pprint = "com.lihaoyi" %% "pprint" % "0.5.2"
  val streamsTck = List(
    "org.reactivestreams" % "reactive-streams-tck" % "1.0.2" % "test",
    "org.reactivestreams" % "reactive-streams-tck-flow" % "1.0.2" % "test")

  val swagger = "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.11.0"
  val cors = "ch.megard" %% "akka-http-cors" % "0.2.2"
  val cats = List("cats-core", "cats-free").map { art =>
    "org.typelevel" %% art % "1.1.0"
  }

  val catsEffect = "org.typelevel" %% "cats-effect" % "1.0.0-RC2"

  val sourcecode = "com.lihaoyi" %% "sourcecode" % "0.1.4"

  val reactiveStreams = "org.reactivestreams" % "reactive-streams" % "1.0.1"

  val Config: List[ModuleID] = config :: testDependencies
  val IO: List[ModuleID] = logging ::: testDependencies
  val Api: List[ModuleID] = reactiveStreams :: logging ::: testDependencies ::: circe
  val Rest: List[ModuleID] = Api ::: akkaHttp ::: cucumber ::: List(pprint, swagger, cors) ::: streamsTck
  val RestApi: List[ModuleID] = Api ::: akkaHttp

  val mongoDriver = List(
    "org.mongodb.scala" %% "mongo-scala-driver" % "2.3.0" //,
//    "org.mongodb.scala" %% "mongodb-driver-reactivestreams" % "1.8.0"
  )
  val monix = List("monix", "monix-execution",  "monix-eval", "monix-reactive", "monix-tail").map { art =>
    "io.monix" %% art % "3.0.0-RC1"
  } ++ List(
    "org.atnos" %% "eff" % "5.2.0", // http://atnos-org.github.io/eff/org.atnos.site.Installation.html
    "org.atnos" %% "eff-monix" % "5.2.0"
  )

  val FlowBase: List[ModuleID] = reactiveStreams :: cats ::: logging ::: testDependencies ::: streamsTck ::: monix ::: mongoDriver // ::: circe
  val Flow: List[ModuleID] = circe ::: FlowBase
  val Json: List[ModuleID] = config :: circe ::: testDependencies

  val http4s = List("http4s-blaze-server", "http4s-circe", "http4s-dsl").map { art =>
    "org.http4s"      %% art  % "0.18.12"
  }


  val finch = List("finch-core", "finch-circe").map { art =>
    "com.github.finagle" %% art % "0.20.0"
  }

  val Riff = monix ::: cats ::: logging ::: testDependencies

  val vertx = List(
    "io.vertx" %% "vertx-lang-scala" % "3.5.2",
    "io.vertx" %% "vertx-web-scala" % "3.5.2"
  )

  val javaxWSClient = List(
    "javax.websocket" % "javax.websocket-client-api" % "1.1",
    "org.glassfish.tyrus" % "tyrus-client" % "1.1",
    "org.glassfish.tyrus" % "tyrus-container-grizzly" % "1.1"
  )

  //cats :::
  val CrudApi = monix ::: logging ::: testDependencies
  val CrudTyrus = javaxWSClient ::: logging ::: testDependencies
  val CrudFree = catsEffect :: cats ::: logging ::: testDependencies
  val CrudMongo = mongoDriver ::: testDependencies
  val CrudAkkaHttp = akkaHttp ::: testDependencies
  val CrudMonix = monix ::: testDependencies
  val CrudHttp4s = http4s ::: testDependencies
  val CrudUndertow = mongoDriver ::: testDependencies
  val CrudVertx = vertx ::: logging ::: testDependencies
  val CrudFinch  = finch ::: testDependencies
}
