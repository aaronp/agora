package agora.rest.worker

import agora.api.exchange.Exchange
import agora.api.worker.SubscriptionKey
import agora.domain.io.Sources
import akka.http.scaladsl.model.HttpEntity.CloseDelimited
import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.RouteResult.Complete
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging

import scala.util.control.NonFatal

trait RouteSubscriptionSupport extends LazyLogging {

  import akka.http.scaladsl.server.directives.BasicDirectives._

  def takeNextOnComplete(exchange: Exchange, subscription: SubscriptionKey, nrToTake: Int = 1): Directive0 = {

    lazy val takeNext = {
      try {
        exchange.take(subscription, nrToTake)
      } catch {
        case NonFatal(e) =>
          logger.error(s"Error requesting next $nrToTake for $subscription from $exchange: $e", e)
      }
    }

    mapRouteResult {
      case r @ Complete(resp) =>
        val original: ResponseEntity = resp.entity
        original match {
          case CloseDelimited(contentType, data) =>
            import Sources._

            val newData: Source[ByteString, Any] = data.onComplete {
              takeNext
            }
            val newClose = CloseDelimited(contentType, newData)
            Complete(resp.withEntity(newClose))
          case other =>
            logger.error(s"Can't map response for $other")
            r
        }
      case other => other
    }
  }

}
