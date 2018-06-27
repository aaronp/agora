package streaming.vertx.server

import java.util.concurrent.atomic.AtomicBoolean

import io.vertx.core.Handler
import io.vertx.scala.core.http.{ServerWebSocket, WebSocketFrame}
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer, Pipe}
import streaming.api.{Endpoint, WebFrame}

import scala.concurrent.duration.Duration

final class VertxWebSocketEndpoint(val socket: ServerWebSocket, from: Observable[WebFrame], to: Observer[WebFrame]) extends Endpoint[WebFrame, WebFrame](from, to)

object VertxWebSocketEndpoint {
  def apply(socket: ServerWebSocket)(implicit timeout: Duration, scheduler: Scheduler): VertxWebSocketEndpoint = {

    val fromClient: Pipe[WebFrame, WebFrame] = Pipe.publish[WebFrame]
    val (frameSink, frameSource: Observable[WebFrame]) = fromClient.concurrent

    val completed = new AtomicBoolean(false)

    def markComplete() = {
      if (completed.compareAndSet(false, true)) {
        frameSink.onComplete()
      }
    }

    socket.frameHandler(new Handler[WebSocketFrame] {
      override def handle(event: WebSocketFrame): Unit = {
        println(s"Handling frame from client: $event")
        if (event.isClose()) {
          markComplete()
        } else {
          val fut = frameSink.onNext(WebSocketFrameAsWebFrame(event))
          // TODO - we should apply back-pressure, but also not block the event loop.
          // need to apply some thought here if this can work in the general case,
          // of if this should be made more explicit
          //Await.result(fut, timeout)
        }
      }
    })

    socket.accept()
    socket.exceptionHandler(new Handler[Throwable] {
      override def handle(event: Throwable): Unit = {
        frameSink.onError(event)
        socket.close()
      }
    })
    socket.endHandler(new Handler[Unit] {
      override def handle(event: Unit): Unit = markComplete()
    })
    new VertxWebSocketEndpoint(socket, frameSource, ServerWebSocketObserver(socket))
  }
}
