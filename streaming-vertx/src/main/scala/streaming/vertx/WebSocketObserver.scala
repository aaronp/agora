package streaming.vertx

import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.LazyLogging
import io.vertx.core.buffer.Buffer
import io.vertx.scala.core.http.WebSocketBase
import monix.execution.Ack
import monix.execution.Ack.{Continue, Stop}
import monix.reactive.Observer
import streaming.api._

import scala.concurrent.Future
import scala.util.control.NonFatal

private[vertx] final case class WebSocketObserver(socket: WebSocketBase) extends Observer[WebFrame] with LazyLogging {
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

  private val completed = new AtomicBoolean(false)

  override def onComplete(): Unit = {

    try {
      if (completed.compareAndSet(false, true)) {
        socket.end()
      }
    } catch {
      case NonFatal(e) =>
        logger.error(s"Error ending socket connected to ${socket.remoteAddress()}", e)
    }
  }
}
