package jabroni.rest.worker

import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{HttpResponse, ResponseEntity}
import akka.http.scaladsl.server.Directives.{complete, extractRequest, path, pathPrefix, _}
import akka.http.scaladsl.server.Route
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.{RequestWorkAck, WorkSubscription, WorkSubscriptionAck}
import jabroni.api.worker.SubscriptionKey
import jabroni.rest.MatchDetailsExtractor
import jabroni.rest.multipart.{MultipartDirectives, MultipartPieces}

import scala.concurrent.Future
import scala.language.reflectiveCalls

// TODO- I'm sure we can remove this entirely, just using a multipart unmarshaller
trait MultipartRouteSupport extends MultipartDirectives {
  self: WorkerRoutes =>

  def multipartRoutes: Route = post {
    pathPrefix("rest" / "multipart") {
      extractRequest { request =>
        path(Remaining) { remaining =>

          println(s"Checking multipart '$remaining' against ${availableMultipartHandlers}")
          findOnWork(remaining) match {
            case None => reject
            case Some(worker) =>
              multipartData { (sourcesByKey: MultipartPieces) =>
                complete {
                  val details = MatchDetailsExtractor.unapply(request)
                  val httpResp = worker.handle(details, sourcesByKey)
                  Future.successful(httpResp)
                }
              }
          }
        }
      }
    }
  }

  private object MultipartHandlerLock

  private var multipartByPath = Map[String, OnMultipartWork]()

  private def availableMultipartHandlers: String = multipartByPath.keySet.toList.sorted.mkString("[", ",", "]")

  def handleMultipart(onReq: WorkContext[MultipartPieces] => ResponseEntity)
                     (implicit subscription: WorkSubscription = defaultSubscription,
                      initialRequest: Int = defaultInitialRequest): Future[RequestWorkAck] = {

    val path = subscription.details.path.getOrElse(sys.error(s"The subscription doesn't contain a path: ${subscription.details}"))
    val subscriptionAckFuture: Future[WorkSubscriptionAck] = exchange.subscribe(subscription)

    val handler = new OnMultipartWork(subscription, initialRequest, onReq)

    MultipartHandlerLock.synchronized {
      multipartByPath.get(path).foreach { oldHandler =>
        // we'd have to consider this use case and presumably cancel the old subscription
        sys.error(s"Replacing handlers isn't supported: $oldHandler")
      }
      multipartByPath = multipartByPath.updated(path, handler)
    }

    subscriptionAckFuture.flatMap { ack =>

      // update our handler with it's subscription key (see 'OnWork' comment for why it has to be created first,
      // before we have this subscription key)
      setSubscriptionKeyOnMultipartHandler(path, ack.id)

      // ask for some initial work
      if (initialRequest > 0) {
        exchange.take(ack.id, initialRequest)
      } else {
        Future.successful(RequestWorkAck(ack.id, initialRequest))
      }
    }
  }


  private def setSubscriptionKeyOnMultipartHandler(path: String, key: SubscriptionKey) = {
    MultipartHandlerLock.synchronized {
      multipartByPath.get(path).foreach { handler =>
        require(handler.key.isEmpty, "Key was already chuffing set!?@?")
        handler.key = Option(key)
      }
    }
  }

  private class OnMultipartWork(subscription: WorkSubscription, initialRequest: Int, onReq: WorkContext[MultipartPieces] => ResponseEntity) {
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

    def handle(details: Option[MatchDetails], req: MultipartPieces): HttpResponse = {
      val context = WorkContext(exchange, key, subscription, details, req)
      val respEntity: ResponseEntity = onReq(context)
      HttpResponse(entity = respEntity)
    }
  }


  private def findOnWork(workerName: String): Option[OnMultipartWork] = MultipartHandlerLock.synchronized {
    multipartByPath.get(workerName)
  }
}
