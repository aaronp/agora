package agora.exec

import agora.api.exchange.Exchange
import agora.exec.rest.{ExecutionRoutes, UploadRoutes}
import agora.exec.workspace.WorkspaceClient
import agora.rest.RunningService
import agora.rest.exchange.ExchangeRoutes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors

import scala.concurrent.Future

object ExecBoot {

  def apply(conf: ExecConfig): ExecBoot = {
    import conf._

    // either attach to or create a new exchange
    val (exchange: Exchange, optionalExchangeRoutes: Option[Route]) = if (includeExchangeRoutes) {

      /**
        * Should we connect to another exchange or this one?
        */
      val localExchange = exchangeConfig.newExchange
      val exchange: Exchange = if (exchangeConfig.location == location) {
        localExchange
      } else {
        exchangeClient
      }
      val exRoutes: ExchangeRoutes = exchangeConfig.newExchangeRoutes(localExchange)
      (exchange, Option(exRoutes.routes))
    } else {
      (exchangeClient, None)
    }

    new ExecBoot(conf, exchange, optionalExchangeRoutes)
  }

}

/**
  * Provides functions for setting up the exec service functions
  */
case class ExecBoot(conf: ExecConfig, exchange: Exchange, optionalExchangeRoutes: Option[Route]) extends FailFastCirceSupport with StrictLogging {

  lazy val workspaceClient: WorkspaceClient = conf.workspaceClient

  /** @return a future of the ExecutionRoutes once the exec subscription completes
    */
  def executionRoutes = new ExecutionRoutes(conf, exchange, workspaceClient)

  def uploadRoutes: Route = UploadRoutes(workspaceClient).uploadRoute

  /**
    * Start the service.
    *
    * It creates two subscriptions -- one for just running and executing, and another for executing and jobs
    * which just return the exit code.
    */
  def start(): Future[RunningService[ExecConfig, ExecutionRoutes]] = {
    import conf.serverImplicits._
    val execRoutes = executionRoutes
    val restRoutes = {
      cors()(
        uploadRoutes ~ execRoutes.routes(optionalExchangeRoutes)
      )
    }

    logger.info(s"Starting Execution Server in ${conf.location}")
    val startFuture         = RunningService.start[ExecConfig, ExecutionRoutes](conf, restRoutes, execRoutes)
    val execSubscribeFuture = exchange.subscribe(conf.execSubscription)
    for {
      rs   <- startFuture
      ack1 <- execSubscribeFuture
      execAndSaveSubscription = conf.execAndSaveSubscription.referencing(ack1.id)
      ack2 <- exchange.subscribe(execAndSaveSubscription)
      _    <- exchange.take(ack2.id, conf.initialExecutionSubscription)
    } yield {
      rs
    }
  }
}
