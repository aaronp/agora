package agora.exec

import agora.api.SubscriptionKey
import agora.api.exchange.Exchange
import agora.exec.rest.ExecutionRoutes
import agora.rest.RunningService
import agora.rest.exchange.ExchangeRoutes
import agora.rest.worker.DynamicWorkerRoutes
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.Future

object ExecBoot {

  def apply(conf: ExecConfig) = {
    import conf._

    // either attach to or create a new exchange
    val (exchange: Exchange, optionalExchangeRoutes: Option[Route]) = if (includeExchangeRoutes) {
      val localExchange = exchangeConfig.newExchange
      val exRoutes: ExchangeRoutes = exchangeConfig.newExchangeRoutes(localExchange)
      (localExchange, Option(exRoutes.routes))
    } else {
      (exchangeClient, None)
    }

    new ExecBoot(conf, exchange, optionalExchangeRoutes)
  }

}

/**
  * Provides functions for setting up the exec service functions
  */
case class ExecBoot(conf: ExecConfig, exchange: Exchange, optionalExchangeRoutes: Option[Route]) extends FailFastCirceSupport {

  val execSubscription = ExecutionRoutes.execSubscription()

  val uploadSubscription = ExecutionRoutes.uploadSubscription()

  import conf._
  import conf.serverImplicits._

  // create a worker to subscribe to the exchange
  def workerRoutes: DynamicWorkerRoutes = newWorkerRoutes(exchange)

  /** @return a future of the ExecutionRoutes once the exec subscription completes
    */
  def executionRoutes: Future[ExecutionRoutes] = {
    val execAckFut = exchange.subscribe(execSubscription)
    val uploadAckFut = exchange.subscribe(uploadSubscription)

    // subscribe to handle 'execute' requests
    for {
      execAck <- execAckFut
      uploadAck <- uploadAckFut
    } yield {
      new ExecutionRoutes(conf, exchange, execAck.id, uploadAck.id)
    }
  }

  /**
    * Start the service, subscribe for upload and exec routes, then requests work items
    */
  def start: Future[RunningService[ExecConfig, ExecutionRoutes]] = {
    val execRoutesFuture = executionRoutes

    for {
      execRoutes <- execRoutesFuture
      restRoutes = execRoutes.routes(workerRoutes, optionalExchangeRoutes)
      rs <- RunningService.start[ExecConfig, ExecutionRoutes](conf, restRoutes, execRoutes)
      ExecBoot.subscribeToExchange(exchange, execRoutes)
    } yield {
      rs
    }
  }
}
