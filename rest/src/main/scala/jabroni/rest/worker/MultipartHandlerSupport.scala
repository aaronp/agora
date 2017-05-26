package jabroni.rest.worker

import akka.http.scaladsl.model.{HttpResponse, Multipart}
import akka.http.scaladsl.server.Directives.{complete, path, pathPrefix, _}
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.{RequestContext, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import jabroni.api.exchange.{RequestWorkAck, WorkSubscription, WorkSubscriptionAck}
import jabroni.api.worker.SubscriptionKey

import scala.concurrent.Future
import scala.language.reflectiveCalls

// TODO- I'm sure we can remove this entirely, just using a multipart unmarshaller
trait MultipartHandlerSupport extends FailFastCirceSupport {
  self: WorkerRoutes =>

  def multipartRoutes: Route = post {
    pathPrefix("rest" / "multipart") {
      extractRequestContext { ctxt: RequestContext =>

        path(Remaining) { remaining =>
          findOnWork(remaining) match {
            case None => reject
            case Some(worker) =>
              entity(as[Multipart.FormData]) { (formData: Multipart.FormData) =>
                complete {
                  worker.handle(ctxt, formData)
                }
              }
          }
        }
      }
    }
  }

  private object MultipartHandlerLock

  protected var multipartByPath = Map[String, OnMultipartWork]()

  def addMultipartHandler(onReq: WorkContext[Multipart.FormData] => Unit)
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


  protected class OnMultipartWork(val subscription: WorkSubscription, initialRequest: Int, onReq: WorkContext[Multipart.FormData] => Unit) {
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

    def handle(ctxt: RequestContext, req: Multipart.FormData): Future[HttpResponse] = {
      val context = WorkContext(exchange, key, subscription, ctxt, req)
      onReq(context)
      context.responseFuture
    }
  }


  private def findOnWork(workerName: String): Option[OnMultipartWork] = MultipartHandlerLock.synchronized {
    multipartByPath.get(workerName)
  }
}
