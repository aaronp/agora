package agora.rest.worker.ws

import agora.api.exchange.WorkSubscription
import agora.rest.worker.WorkerConfig
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

class WebSocketWorkerRoutes(workerConfig: WorkerConfig)(implicit mat: Materializer) extends StrictLogging with FailFastCirceSupport { self =>
  implicit val ec = mat.executionContext

  def routes: Route = {
    pathPrefix("rest" / "worker-ws") {
      createSocketWorker
    }
  }

  def createSocketWorker: Route = post {
    (path("create") & pathEnd) {
      extractRequestContext { requestCtxt =>
        import io.circe.generic.auto._
        entity(as[WorkSubscription]) { subscription =>
          requestCtxt.request.header[UpgradeToWebSocket] match {
            case None => complete(HttpResponse(400, entity = "Not a valid websocket request"))
            case Some(upgrade) =>
              logger.debug("upgrading create worker to web socket")

              complete {
                //upgrade.handleMessages(new WebsocketWorkerFl(workerConfig, subscription).flow)
                ???
              }
          }
        }
      }
    }
  }

}
