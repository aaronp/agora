package crud.vertx

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

      case class SocketHandler(name: String) extends Observer[WebSocketFrame] {
        override def onNext(elem: WebSocketFrame): Future[Ack] = {
          println(s"$name got : " + elem.textData())
          Ack.Continue
        }

        override def onError(ex: Throwable): Unit = {
          println(s"$name handling " + ex)
        }

        override def onComplete(): Unit = {
          println(s"$name done")
        }
      }

      // start the server
      val started: ScalaVerticle = CrudVertxServer.start(port) { socket =>
        socket.writeTextMessage("hello from the server")
        SocketHandler("server")
      }

      val c: CrudVertxClient = CrudVertxClient.start(ResolvedEndpoint(port, "/some/path")) { socket: WebSocket =>
        socket.writeTextMessage("from the client")
        SocketHandler("client")
      }

      Thread.sleep(1000)
      println("done")
    }
  }

}
