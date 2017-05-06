package jabroni.rest.worker

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import jabroni.api.exchange.{Exchange, RequestWorkAck, WorkSubscription, WorkSubscriptionAck}
import jabroni.api.worker.SubscriptionKey
import jabroni.health.HealthDto
import jabroni.rest.MatchDetailsExtractor

import scala.concurrent.Future
import scala.language.reflectiveCalls

case class WorkerRoutes(exchange: Exchange,
                        defaultSubscription: WorkSubscription,
                        defaultInitialRequest: Int)(implicit mat: Materializer) extends FailFastCirceSupport {

  import mat._

  // http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html

  private object HandlerWriteLock

  private var workerByPath = Map[String, OnWork[_, _]]()

  def available = workerByPath.keySet.toList.sorted.mkString("[", ",", "]")

  def routes: Route = workerRoutes ~ health


  def health = (get & path("health") & pathEnd) {
    import io.circe.syntax._
    complete {
      HttpResponse(entity = HttpEntity(`application/json`, HealthDto().asJson.noSpaces))
    }
  }

  def workerRoutes: Route = pathPrefix("rest" / "worker") {
    extractRequest { request =>

      path(Remaining) { remaining =>
        find(remaining) match {
          case None =>
            complete(
              HttpResponse(BadRequest, entity = s"No handler found for '$remaining'. Available handlers are: ${available}")
            )
          case Some(worker) =>
            complete {
              worker.handle(request)
            }
        }
      }
    }
  }

  private class OnWork[T, R: Encoder](subscription: WorkSubscription, initialRequest: Int, unmarshaller: Unmarshaller[HttpRequest, T], onReq: WorkContext[T] => R) {
    /**
      * we have the case where a worker can actually handle a request at any time from a client ... even before we bother
      * subscribing to the exchange.
      *
      * This isn't even a race condition ... there's nothing to say requests to a worker 'microservice' (ahem) has to
      * have gone through our exchange at all.
      *
      * Hence, the subscription key is optional
      */
    var key: Option[SubscriptionKey] = None

    def handle(req: HttpRequest): Future[HttpResponse] = {

      import io.circe.syntax._

      unmarshaller(req).map { tea =>
        val details = MatchDetailsExtractor.unapply(req)

        val context = WorkContext(exchange, key, subscription, details, tea)
        val response = onReq(context)
        HttpResponse(entity = HttpEntity(`application/json`, response.asJson.noSpaces))
      }
    }
  }


  /**
    * Add a handler
    *
    * @param onReq
    * @param subscription
    * @param initialRequest
    * @tparam T
    * @tparam R
    * @return
    */
  def handle[T: Decoder, R: Encoder](onReq: WorkContext[T] => R)(implicit subscription: WorkSubscription = defaultSubscription, initialRequest: Int = defaultInitialRequest): Future[RequestWorkAck] = {

    val resp: Future[WorkSubscriptionAck] = exchange.subscribe(subscription)
    val path = subscription.details.path.getOrElse(sys.error(s"The subscription doesn't contain a path: ${subscription.details}"))

    val u: FromEntityUnmarshaller[T] = unmarshaller[T]
    val fromRequest: Unmarshaller[HttpRequest, T] = implicitly[Unmarshaller[HttpRequest, T]]
    val handler = new OnWork[T, R](subscription, initialRequest, fromRequest, onReq)

    HandlerWriteLock.synchronized {
      workerByPath.get(path).foreach { oldHandler =>
        // we'd have to consider this use case and presumably cancel the old subscription
        sys.error(s"Replacing handlers isn't supported: $oldHandler")
      }
      workerByPath = workerByPath.updated(path, handler)
    }

    resp.flatMap { ack =>
      setSubscriptionKeyOnHandler(path, ack.id)
      if (initialRequest > 0) {
        exchange.take(ack.id, initialRequest)
      } else {
        Future.successful(RequestWorkAck(ack.id, initialRequest))
      }
    }
  }

  private def setSubscriptionKeyOnHandler(path: String, key: SubscriptionKey) = {
    HandlerWriteLock.synchronized {
      workerByPath.get(path).foreach { handler =>
        require(handler.key.isEmpty, "Key was already chuffing set!?@?")
        handler.key = Option(key)
      }
    }
  }

  private def find(workerName: String) = workerByPath.get(workerName)
}