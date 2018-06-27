package streaming.vertx.server

import io.vertx.core.buffer.Buffer
import io.vertx.scala.core.http.ServerWebSocket
import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.reactive.Observer
import streaming.api._

import scala.concurrent.Future

final case class ServerWebSocketObserver(socket: ServerWebSocket) extends Observer[WebFrame] {
  override def onNext(elem: WebFrame): Future[Ack] = elem match {
    case TextFrame(text) =>
      socket.writeTextMessage(text)
      Continue
    case BinaryFrame(data) =>
      val buff: Buffer = io.vertx.core.buffer.Buffer.buffer(data.array)
      socket.writeBinaryMessage(buff)
      Continue
    case FinalTextFrame(text) =>
      socket.writeFinalTextFrame(text)
      Continue
    case FinalBinaryFrame(data) =>
      val buff: Buffer = io.vertx.core.buffer.Buffer.buffer(data.array)
      socket.writeFinalBinaryFrame(buff)
      Continue
    case CloseFrame(statusCode, reason) =>
      socket.close(statusCode, reason)
      Stop
  }

  override def onError(ex: Throwable): Unit = {
    socket.close(500, Option(s"Error: $ex"))
  }

  override def onComplete(): Unit = {
    socket.close()
  }
}
