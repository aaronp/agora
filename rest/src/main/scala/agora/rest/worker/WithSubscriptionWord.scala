package agora.rest.worker

import agora.api.exchange.{RequestWorkAck, WorkSubscription}
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller

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
class WithSubscriptionWord private[worker] (routes: WorkerRoutes, f: WorkSubscription => WorkSubscription, initialRequestOpt: Option[Int]) {

  def withInitialRequest(n: Int) = {
    new WithSubscriptionWord(routes, f, Option(n))
  }

  /**
    * Adds the handler to the worker routes and creates a subscription to the exchange
    * @param onReq
    * @param subscription
    * @param fromRequest
    * @tparam T
    * @return
    */
  def addHandler[T](onReq: WorkContext[T] => Unit)(implicit subscription: WorkSubscription = routes.defaultSubscription,
                                                   fromRequest: FromRequestUnmarshaller[T]): Future[RequestWorkAck] = {
    val newSubscription = f(subscription)
    routes.addHandler(onReq)(newSubscription, initialRequestOpt.getOrElse(routes.defaultInitialRequest), fromRequest)
  }

}
