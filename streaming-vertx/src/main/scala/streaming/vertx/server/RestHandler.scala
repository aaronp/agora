package streaming.vertx.server

import agora.io.{FromBytes, ToBytes}
import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.scala.core.http.HttpServerRequest
import monix.reactive.{Observable, Observer, Pipe}
import streaming.rest.{HttpMethod, WebURI}

import scala.util.{Failure, Success, Try}

class RestHandler(feed: Observer[HttpServerRequest], val requests: Observable[HttpServerRequest]) extends Handler[HttpServerRequest] with StrictLogging {
  override def handle(event: HttpServerRequest): Unit = {
    logger.debug(s"pushing $event")
    feed.onNext(event)
  }

//  def respondWith[In: FromBytes, Out: ToBytes](onRequest: PartialFunction[WebURI, In => Out]): Observable[HttpServerRequest] = {
//    requests.map { req: HttpServerRequest =>
//
//      logger.debug(s"mapped handling $req")
//
//      val method = HttpMethod.unapply(req.method().name()).getOrElse(sys.error(s"Couldn't parse method ${req.method}"))
//      val uri = method -> req.uri()
//      if (onRequest.isDefinedAt(    uri)) {
//
//      } else {
//        val msg = s"No route found for ${uri}"
//        logger.warn(msg)
//        req.response().setStatusCode(404)
//        req.response().end(msg)
//      }
//
//      req
//    }
//  }
}

object RestHandler extends StrictLogging {

  def asObservable[In: FromBytes, Out: ToBytes](req: HttpServerRequest)(handler: In => Out) = {

    //    val handler = onRequest(uri)

    req.handler { event: Buffer =>
      logger.debug("Handling content...")
      val body: Try[In] = FromBytes[In].read(event.getBytes)
      body.map(handler) match {
        case Success(reply) =>
          import ToBytes.ops._
          val data = Buffer.buffer(reply.bytes)
          req.response().setStatusCode(200)
          req.response().write(data)
        case Failure(err) =>
          req.response().setStatusCode(500)
          req.response().setStatusMessage(s"Error handling $body: $err")
      }
      logger.debug("ending response")
      req.response().end()
    }

  }

  def apply(): RestHandler = {
    val (feed, requests) = Pipe.publish[HttpServerRequest].unicast
    new RestHandler(feed, requests)
  }
}
