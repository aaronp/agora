package streaming.vertx

import java.util.concurrent.{CountDownLatch, TimeUnit}

import crud.api.BaseCrudApiSpec
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.http.{WebSocket, WebSocketFrame}
import monix.execution.Ack
import monix.reactive.Observer
import streaming.api.{EndpointCoords, WebFrame}
import streaming.vertx.client.StreamingVertxClient
import streaming.vertx.server.{StreamingVertxServer, VertxWebSocketEndpoint}
import monix.execution.Scheduler.Implicits.global
import scala.concurrent.Future

class StreamingVertxClientTest extends BaseCrudApiSpec {

  "StreamingVertxClient" should {
    "connect to a server" in {

      val port = 1234

      case class SocketHandler(name: String)(textHandler: String => Unit) extends Observer[WebSocketFrame] {
        override def onNext(elem: WebSocketFrame): Future[Ack] = {
          println(s"$name got : " + elem.textData())
          textHandler(elem.textData())
          Ack.Continue
        }

        override def onError(ex: Throwable): Unit = {
          println(s"$name handling " + ex)
        }

        override def onComplete(): Unit = {
          println(s"$name done")
        }
      }
      val receivedFromServer = new CountDownLatch(1)
      var fromServer = ""
      val receivedFromClient = new CountDownLatch(1)
      var fromClient = ""

      // start the server
      val started: ScalaVerticle = StreamingVertxServer.start(port) { endpoint: VertxWebSocketEndpoint =>


        println(
          s"""
             |  uri=${endpoint.socket.uri}
             | path=${endpoint.socket.path}
             |query=${endpoint.socket.query}
          """.stripMargin)
        //        SocketHandler("server") { msg =>
        //        }
        val text: WebFrame = WebFrame.text(s"hello from the server at ${endpoint.socket.path}")
        endpoint.toRemote.onNext(text)

        endpoint.fromRemote.foreach { msg: WebFrame =>
          receivedFromClient.countDown()
          msg.asText.foreach(fromClient = _)
        }

      }

      val c: StreamingVertxClient = StreamingVertxClient.start(EndpointCoords(port, "/some/path")) { socket: WebSocket =>
        socket.writeTextMessage(s"from the client")
        SocketHandler("client") { msg =>
          receivedFromServer.countDown()
          fromServer = msg
        }
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