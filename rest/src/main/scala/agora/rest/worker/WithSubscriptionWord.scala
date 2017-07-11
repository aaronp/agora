package agora.rest.worker

import akka.http.scaladsl.model.{HttpRequest, Multipart}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import agora.api.exchange.{RequestWorkAck, WorkSubscription}

import scala.concurrent.Future

/**
  * Class just to support a DSL so you can have:
  *
  * {{{
  *   val routes : WorkerRoutes = ???
  *   routes.withSubscription(XYZ).addHandler( ctxt => ... )
  *
  * }}}
  *
  * @param routes the worker routes
  * @param f the handler
  */
class WithSubscriptionWord private[worker] (routes: WorkerRoutes, f: WorkSubscription => WorkSubscription) {

  def addHandler[T](onReq: WorkContext[T] => Unit)(implicit subscription: WorkSubscription = routes.defaultSubscription,
                                                   initialRequest: Int = routes.defaultInitialRequest,
                                                   fromRequest: Unmarshaller[HttpRequest, T]): Future[RequestWorkAck] = {
    val newSubscription = f(subscription)
    routes.addHandler(onReq)(newSubscription, initialRequest, fromRequest)
  }

}
