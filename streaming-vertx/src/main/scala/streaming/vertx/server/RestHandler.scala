package streaming.vertx.server

import agora.io.{FromBytes, ToBytes}
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.scala.core.http.HttpServerRequest
import monix.reactive.{Observable, Observer, Pipe}

import scala.util.{Failure, Success, Try}

class RestHandler(feed: Observer[HttpServerRequest], val requests: Observable[HttpServerRequest]) extends Handler[HttpServerRequest] {
  override def handle(event: HttpServerRequest): Unit = {
    feed.onNext(event)
  }

  def respondWith[In: FromBytes, Out: ToBytes](onRequest: In => Out): Observable[HttpServerRequest] = {
    requests.map { req =>
      req.handler(new Handler[Buffer] {
        override def handle(event: Buffer): Unit = {
          val body: Try[In] = FromBytes[In].read(event.getBytes)
          body.map(onRequest) match {
            case Success(reply) =>
              import ToBytes.ops._
              val data = Buffer.buffer(reply.bytes)
              req.response().setStatusCode(200)
              req.response().write(data)
            case Failure(err) =>
              val data = Buffer.buffer()
              req.response().setStatusCode(500)
              req.response().setStatusMessage(s"Error handling $body: $err")
              req.response().close()
          }
        }
      })
    }
  }
}

object RestHandler {
  def apply(): RestHandler = {
    val (feed, requests) = Pipe.publish[HttpServerRequest].unicast
    new RestHandler(feed, requests)
  }
}
