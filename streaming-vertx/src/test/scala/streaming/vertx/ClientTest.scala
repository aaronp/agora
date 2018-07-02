package streaming.vertx

import java.util.concurrent.{CountDownLatch, TimeUnit}

import io.vertx.lang.scala.ScalaVerticle
import streaming.api.{BaseStreamingApiSpec, EndpointCoords, WebFrame}
import streaming.vertx.client.Client
import streaming.vertx.server.{Server, ServerEndpoint}

class ClientTest extends BaseStreamingApiSpec {

  "Client" should {
    "connect to a server" in {

      val port = 1234

      val receivedFromServer = new CountDownLatch(1)
      var fromServer = ""
      val receivedFromClient = new CountDownLatch(1)
      var fromClient = ""

      // start the server
      val started: ScalaVerticle = Server.start(port) { endpoint: ServerEndpoint =>

        endpoint.toRemote.onNext(WebFrame.text(s"hello from the server at ${endpoint.socket.path}"))

        endpoint.fromRemote.foreach { msg: WebFrame =>
          msg.asText.foreach(fromClient = _)
          receivedFromClient.countDown()
        }
      }

      val c: Client = Client.connect(EndpointCoords(port, "/some/path")) { endpoint =>
        endpoint.fromRemote.foreach { msg =>
          msg.asText.foreach(fromServer = _)
          receivedFromServer.countDown()
        }
        endpoint.toRemote.onNext(WebFrame.text("from the client"))
      }

      receivedFromServer.await(testTimeout.toMillis, TimeUnit.MILLISECONDS)
      receivedFromClient.await(testTimeout.toMillis, TimeUnit.MILLISECONDS)

      fromServer shouldBe "hello from the server at /some/path"
      fromClient shouldBe "from the client"

      c.stop()
      started.stop()
    }
  }
}