package agora.rest.worker

import akka.http.scaladsl.model.{HttpRequest, Multipart}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import agora.api.exchange.{RequestWorkAck, WorkSubscription}

import scala.concurrent.Future

class WithSubscriptionWord private[worker] (routes: WorkerRoutes, f: WorkSubscription => WorkSubscription) {

  def addHandler[T](onReq: WorkContext[T] => Unit)(implicit subscription: WorkSubscription = routes.defaultSubscription,
                                                   initialRequest: Int = routes.defaultInitialRequest,
                                                   fromRequest: Unmarshaller[HttpRequest, T]): Future[RequestWorkAck] = {
    val newSubscription = f(subscription)
    routes.addHandler(onReq)(newSubscription, initialRequest, fromRequest)
  }

  def addMultipartHandler(onReq: WorkContext[Multipart.FormData] => Unit)(implicit subscription: WorkSubscription = routes.defaultSubscription,
                                                                          initialRequest: Int = routes.defaultInitialRequest): Future[RequestWorkAck] = {
    val newSubscription = f(subscription)
    routes.addMultipartHandler(onReq)(newSubscription, initialRequest)
  }
}
