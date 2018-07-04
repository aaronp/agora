package streaming.vertx

import java.util.concurrent.atomic.AtomicBoolean

import io.vertx.core.Handler
import io.vertx.scala.core.http.{WebSocketBase, WebSocketFrame}
import monix.execution.Scheduler
import monix.reactive.{Observable, Pipe}
import streaming.api.WebFrame
import streaming.vertx.server.WebSocketFrameAsWebFrame

import scala.concurrent.duration.Duration

object WebFrameEndpoint {

  def replay(socket: WebSocketBase)(implicit timeout: Duration, scheduler: Scheduler): (WebSocketObserver, Observable[WebFrame]) = {

    val (frameSink, frameSource: Observable[WebFrame]) = Pipe.replay[WebFrame].concurrent

    val completed = new AtomicBoolean(false)

    def markComplete() = {
      if (completed.compareAndSet(false, true)) {
        frameSink.onComplete()
      }
    }

    socket.frameHandler(new Handler[WebSocketFrame] {
      override def handle(event: WebSocketFrame): Unit = {
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

    socket.exceptionHandler(new Handler[Throwable] {
      override def handle(event: Throwable): Unit = {
        frameSink.onError(event)
        socket.close()
      }
    })
    socket.endHandler(new Handler[Unit] {
      override def handle(event: Unit): Unit = markComplete()
    })
    (WebSocketObserver(socket), frameSource)
  }
}
