package streaming.vertx.client

import io.vertx.scala.core.http.{HttpClientResponse, HttpServerRequest}
import monix.eval.MVar
import monix.reactive.Observable
import streaming.api.{BaseStreamingApiSpec, HostPort}
import streaming.rest.{EndpointCoords, WebURI}
import streaming.vertx.server.Server

import scala.concurrent.duration.{FiniteDuration, _}

class RestClientTest extends BaseStreamingApiSpec {

  "RestClient.send" should {
    "send and receive shit" in {
      val Index = WebURI.get("/index.html")
      val Save: WebURI = WebURI.post("/save/:name")
      val Read = WebURI.get("/save/:name")
      val port = 8000


      val routes = Map(
        Index -> s"got index.html, ignoring '${(_: String)}'",
      Save -> { (body: String) => s"saving ${params("name")} w/ body '$body'" },
      Read -> { (body: String) => s"reading ${params("name")} w/ body '$body'" }

      )
//      val server: Observable[HttpServerRequest] =
        Server.startRest(HostPort.localhost(port), routes)

//      server.foreach { req =>
//        println(s".>>>> handling ${req.uri()}")
//      }
      println(s"Running on $port")
      println(s"Running on $port")

      val client = RestClient.connect(EndpointCoords(HostPort.localhost(port), Index))

      try {
        val response = client.send(RestInput(Index))
        val reply: List[HttpClientResponse] = response.toListL.runSyncUnsafe(testTimeout)
        reply.size shouldBe 1
        val bodyReply = MVar.empty[String]
        reply.head.handler { buffer =>
          bodyReply.flatMap(_.put(new String(buffer.getBytes)))
        }

        val bodyAsString: String = bodyReply.flatMap(_.read).runSyncUnsafe(testTimeout)
        println(bodyAsString)
      } finally {
        client.stop()
      }
    }
  }

  "RestClient.sendPipe" ignore {
    "send and receive shit" in {
      val Index = WebURI.get("/index.html")
      val Save = WebURI.post("/save/:name")
      val Read = WebURI.get("/save/:name")
      val port = 8000
      val server: Observable[HttpServerRequest] = Server.startRest(HostPort.localhost(port)) {
        case Index(_) => (body: String) => s"got index.html, ignoring '$body'"
        case Save(params) => (body: String) => s"saving ${params("name")} w/ body '$body'"
        case Read(params) => (body: String) => s"reading ${params("name")} w/ body '$body'"
      }

      server.foreach { req =>
        println(s".>>>> handling ${req.uri()}")
      }
      println(s"Running on $port")
      println(s"Running on $port")

      val client = RestClient.connect(EndpointCoords(HostPort.localhost(port), Index))
      try {

        val requests = List(
          RestInput(Index),
          RestInput(Save, Map("name" -> "david")),
          RestInput(Save, Map("invalid" -> "no name")),
          RestInput(Read)
        )
        val responses: Observable[HttpClientResponse] = Observable.fromIterable(requests).pipeThrough(client.sendPipe)
        val all = responses.toListL.runSyncUnsafe(testTimeout)

        all.foreach(println)
        all.size shouldBe requests.size - 1

      } finally {
        client.stop()
      }
    }
  }

  override implicit def testTimeout: FiniteDuration = 800000.seconds
}
