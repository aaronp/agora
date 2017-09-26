package agora.rest

import akka.event.Logging.LogLevel
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.{DebuggingDirectives, LogEntry, LoggingMagnet}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import scala.concurrent.Future

trait RouteLoggingSupport {

  def entityAsString(entity: HttpEntity)(implicit m: Materializer): Future[String] = {
    entity.dataBytes
      .map(_.decodeString(entity.contentType.charsetOption.get.value))
      .runWith(Sink.head)
  }

  def logRoute(route: Route, level: LogLevel = Logging.InfoLevel)(implicit m: Materializer): Route = {
    import m._
    def myLoggingFunction(logger: LoggingAdapter)(req: HttpRequest)(res: Any): Unit = {
      val entry = res match {
        case Complete(resp) =>
          entityAsString(resp.entity).map(data ⇒
            LogEntry(s"${req.method} ${req.uri}: ${resp.status} \n entity: $data", level))
        case other =>
          Future.successful(LogEntry(s"$other", level))
      }
      entry.map(_.logTo(logger))
    }

    DebuggingDirectives.logRequestResult(LoggingMagnet(log => myLoggingFunction(log)))(route)
  }

}
