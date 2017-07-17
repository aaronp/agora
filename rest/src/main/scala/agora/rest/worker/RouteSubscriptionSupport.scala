package agora.rest.worker

import agora.api.exchange.Exchange
import agora.api.worker.SubscriptionKey
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.RouteResult.Complete
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging

import scala.util.control.NonFatal

trait RouteSubscriptionSupport extends LazyLogging {

  import akka.http.scaladsl.server.directives.BasicDirectives._

  def takeNextOnComplete(exchange: Exchange, subscription: SubscriptionKey, nrToTake: Int = 1): Directive0 = {
    mapRouteResult {
      case r @ Complete(resp) =>
        val flow = Flow.fromFunction((bytes: ByteString) => bytes)
        val flow2: Flow[ByteString, ByteString, Any] = flow.monitor[Any]() { (mat, mon) =>
          ???
          mat
        }
        val re        = resp.entity
        val newEntity = re.transformDataBytes(flow)
        resp.withEntity(newEntity)
        try {
          exchange.take(subscription, nrToTake)
        } catch {
          case NonFatal(e) =>
            logger.error(s"Error requesting next $nrToTake for $subscription from $exchange: $e", e)
        }
        r
      case other => other
    }
  }

}
