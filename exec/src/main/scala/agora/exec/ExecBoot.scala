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

import scala.concurrent.Future

object ExecBoot {

  def apply(conf: ExecConfig) = {
    import conf._

    // either attach to or create a new exchange
    val (exchange: Exchange, optionalExchangeRoutes: Option[Route]) = if (includeExchangeRoutes) {
      val localExchange            = exchangeConfig.newExchange
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
case class ExecBoot(conf: ExecConfig, exchange: Exchange, optionalExchangeRoutes: Option[Route]) extends FailFastCirceSupport with StrictLogging {

  lazy val workspaceClient: WorkspaceClient = WorkspaceClient(conf.uploadsDir, conf.serverImplicits.system)

  /** @return a future of the ExecutionRoutes once the exec subscription completes
    */
  def executionRoutes = new ExecutionRoutes(conf, exchange, workspaceClient)

  def uploadRoutes: Route = UploadRoutes(workspaceClient).uploadRoute

  /**
    * Start the service, subscribe for upload and exec routes, then requests work items
    */
  def start(): Future[RunningService[ExecConfig, ExecutionRoutes]] = {
    import conf.serverImplicits._
    val execRoutes = executionRoutes
    val restRoutes = execRoutes.routes(optionalExchangeRoutes) ~ uploadRoutes

    val execSubscription = conf.subscription

    logger.info(s"Starting Execution Server in ${conf.location}")
    for {
      rs           <- RunningService.start[ExecConfig, ExecutionRoutes](conf, restRoutes, execRoutes)
      subscribeAck <- exchange.subscribe(execSubscription)
      _            <- exchange.take(subscribeAck.id, conf.initialExecutionSubscription)
    } yield {
      rs
    }
  }
}
