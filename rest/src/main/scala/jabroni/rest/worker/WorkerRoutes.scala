package jabroni.rest.worker

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentType, _}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder, Json}
import jabroni.api.exchange.{RequestWorkAck, WorkSubscription, WorkSubscriptionAck}
import jabroni.api.worker.SubscriptionKey
import jabroni.health.HealthDto
import jabroni.rest.MatchDetailsExtractor
import jabroni.rest.exchange.ExchangeClient

import scala.concurrent.Future
import scala.language.reflectiveCalls

// see http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html
case class WorkerRoutes(exchange: ExchangeClient,
                        defaultSubscription: WorkSubscription,
                        defaultInitialRequest: Int)(implicit mat: Materializer) extends FailFastCirceSupport {

  import mat._

  private object HandlerWriteLock

  private var workerByPath = Map[String, OnWork[_]]()

  /** @return the available route handlers
    */
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

  /**
    * Captures the 'handler' logic for a subscription.
    */
  private class OnWork[T](subscription: WorkSubscription, initialRequest: Int, unmarshaller: Unmarshaller[HttpRequest, T], onReq: WorkContext[T] => ResponseEntity) {
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
      unmarshaller(req).map { tea =>
        val details = MatchDetailsExtractor.unapply(req)

        val context = WorkContext(exchange, key, subscription, details, tea)
        val respEntity: ResponseEntity = onReq(context)
        HttpResponse(entity = respEntity)
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
    * @return
    */
  def handleAny[T](onReq: WorkContext[T] => ResponseEntity)
                  (implicit subscription: WorkSubscription = defaultSubscription,
                   initialRequest: Int = defaultInitialRequest,
                   fromRequest: Unmarshaller[HttpRequest, T]): Future[RequestWorkAck] = {

    val subscriptionAckFuture: Future[WorkSubscriptionAck] = exchange.subscribe(subscription)
    val path = subscription.details.path.getOrElse(sys.error(s"The subscription doesn't contain a path: ${subscription.details}"))

    val handler = new OnWork[T](subscription, initialRequest, fromRequest, onReq)

    HandlerWriteLock.synchronized {
      workerByPath.get(path).foreach { oldHandler =>
        // we'd have to consider this use case and presumably cancel the old subscription
        sys.error(s"Replacing handlers isn't supported: $oldHandler")
      }
      workerByPath = workerByPath.updated(path, handler)
    }

    subscriptionAckFuture.flatMap { ack =>

      // update our handler with it's subscription key (see 'OnWork' comment for why it has to be created first,
      // before we have this subscription key)
      setSubscriptionKeyOnHandler(path, ack.id)

      // ask for some initial work
      if (initialRequest > 0) {
        exchange.take(ack.id, initialRequest)
      } else {
        Future.successful(RequestWorkAck(ack.id, initialRequest))
      }
    }
  }


  /**
    * A generic handler
    *
    * @return
    */
  def handle[In: Decoder, Out: Encoder](onReq: WorkContext[In] => Out)
                                          (implicit subscription: WorkSubscription = defaultSubscription,
                                           initialRequest: Int = defaultInitialRequest): Future[RequestWorkAck] = {
    handleJson[In] { (ctxt: WorkContext[In]) =>
      val resp: Out = onReq(ctxt)
      implicitly[Encoder[Out]].apply(resp)
    }
  }


  def handleJson[T: Decoder](onReq: WorkContext[T] => Json)(implicit subscription: WorkSubscription = defaultSubscription, initialRequest: Int = defaultInitialRequest): Future[RequestWorkAck] = {

    val jsonHandler: WorkContext[T] => ResponseEntity = { ctxt =>
      val response: Json = onReq(ctxt)
      HttpEntity(`application/json`, response.noSpaces)
    }
    handleAny(jsonHandler)
  }


  def handleSource[T: Decoder](onReq: WorkContext[T] => Source[ByteString, Any])
                     (implicit subscription: WorkSubscription = defaultSubscription,
                      initialRequest: Int = defaultInitialRequest,
                      contentType: ContentType = ContentTypes.`application/octet-stream`): Future[RequestWorkAck] = {
    handleAny[T] { (ctxt: WorkContext[T]) =>
      val dataSource: Source[ByteString, Any] = onReq(ctxt)
      HttpEntity(contentType, dataSource)
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