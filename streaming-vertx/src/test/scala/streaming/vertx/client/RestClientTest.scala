package streaming.vertx.client

import monix.reactive.Observable
import streaming.api.{BaseStreamingApiSpec, HostPort}
import streaming.rest._
import streaming.vertx.server.Server

import scala.concurrent.duration.{FiniteDuration, _}

class RestClientTest extends BaseStreamingApiSpec {

  "RestClient.send" should {
    "send and receive shit" in {
      val Index                                    = WebURI.get("/index.html")
      val port                                     = 1234
      val requests: Observable[RestRequestContext] = Server.startRest(HostPort.localhost(port))


      requests.foreach { ctxt =>
        ctxt.completeWith(RestResponse.json(s"""{ "msg" : "handled ${ctxt.request.uri}" }"""))
      }
      val client: RestClient = RestClient.connect(HostPort.localhost(port))

      try {
        val response: Observable[RestResponse] = client.send(RestInput(Index))
        val reply: List[RestResponse]          = response.toListL.runSyncUnsafe(testTimeout)
        reply.size shouldBe 1

        reply.head.bodyAsString shouldBe "{ \"msg\" : \"handled index.html\" }"

      } finally {
        client.stop()
      }
    }
  }

  "RestClient.sendPipe" ignore {
    "send and receive shit" in {
      val Index          = WebURI.get("/index.html")
      val Save           = WebURI.post("/save/:name")
      val Read           = WebURI.get("/save/:name")
      val port           = 8000
      val serverRequests = Server.startRest(HostPort.localhost(port))
      serverRequests.foreach { ctxt => ctxt.completeWith(RestResponse.json("true"))
      }
      println(s"Running on $port")
      println(s"Running on $port")

      val client = RestClient.connect(HostPort.localhost(port))
      try {

        val requests = List(
          RestInput(Index),
          RestInput(Save, Map("name"    -> "david")),
          RestInput(Save, Map("invalid" -> "no name")),
          RestInput(Read)
        )
        val responses = Observable.fromIterable(requests).pipeThrough(client.sendPipe)
        val all       = responses.toListL.runSyncUnsafe(testTimeout)

        all.foreach(println)
        all.size shouldBe requests.size - 1

      } finally {
        client.stop()
      }
    }
  }

  override implicit def testTimeout: FiniteDuration = 800000.seconds
}
