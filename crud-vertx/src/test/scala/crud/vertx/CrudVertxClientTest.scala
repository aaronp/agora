package crud.vertx

import java.util.concurrent.{CountDownLatch, TimeUnit}

import crud.api.{BaseCrudApiSpec, ResolvedEndpoint}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.http.{WebSocket, WebSocketFrame}
import monix.execution.Ack
import monix.reactive.Observer

import scala.concurrent.Future

class CrudVertxClientTest extends BaseCrudApiSpec {

  "CrudVertxClient" should {
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
      val started: ScalaVerticle = CrudVertxServer.start(port) { socket =>
        socket.writeTextMessage(s"hello from the server at ${socket.path}")
        println(
          s"""
             |  uri=${socket.uri}
             | path=${socket.path}
             |query=${socket.query}
          """.stripMargin)
        SocketHandler("server") { msg =>
          receivedFromClient.countDown()
          fromClient = msg
        }
      }

      val c: CrudVertxClient = CrudVertxClient.start(ResolvedEndpoint(port, "/some/path")) { socket: WebSocket =>
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
