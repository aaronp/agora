package streaming.vertx.client

import io.vertx.scala.core.http.{HttpClientResponse, HttpServerRequest}
import monix.reactive.Observable
import streaming.api.{BaseStreamingApiSpec, HostPort}
import streaming.rest.{EndpointCoords, WebURI}
import streaming.vertx.server.Server

class RestClientTest extends BaseStreamingApiSpec {

  "RestClient" should {
    "send and receive shit" in {
      val Index = WebURI.get("/index.html")
      val Save = WebURI.post("/save/:name")
      val Read = WebURI.get("/save/:name")
      val port = 1234
      val server: Observable[HttpServerRequest] = Server.startRest(port) {
        case Index(_) => (body: String) => s"got index.html, ignoring '$body'"
        case Save(params) => (body: String) => s"saving ${params("name")} w/ body '$body'"
        case Read(params) => (body: String) => s"reading ${params("name")} w/ body '$body'"
      }

      server.foreach { req =>
        println(s"handling ${req.uri()}")
      }

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
}
