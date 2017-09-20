package agora.exec

import agora.api.exchange.Exchange
import agora.exec.events.StartedSystem
import agora.exec.rest.{ExecutionRoutes, ExecutionWorkflow, QueryRoutes, UploadRoutes}
import agora.exec.workspace.WorkspaceClient
import agora.health.HealthUpdate
import agora.rest.RunningService
import agora.rest.exchange.ExchangeRoutes
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

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

  def workflow: ExecutionWorkflow = ExecutionWorkflow(
    conf.defaultEnv,
    workspaceClient,
    conf.eventMonitor,
    conf.enableCache)

  /** @return a future of the ExecutionRoutes once the exec subscription completes
    */
  lazy val executionRoutes = {
    new ExecutionRoutes(conf, exchange, workflow)
  }

  def uploadRoutes: Route = UploadRoutes(workspaceClient).routes

  val eventMonitor = conf.eventMonitor

  def queryRoutes: Route = QueryRoutes(eventMonitor).routes(conf.enableSupportRoutes)

  def restRoutes: Route = {
    val corsSettings = {
      val default = CorsSettings.defaultSettings
      default.copy(allowedMethods = default.allowedMethods ++ List(HttpMethods.PUT, HttpMethods.DELETE))
    }
    val baseRoutes = cors(corsSettings)(
      uploadRoutes ~ executionRoutes.routes(optionalExchangeRoutes)
    )

    if (conf.eventMonitorConfig.enabled) {
      baseRoutes ~ queryRoutes
    } else {
      baseRoutes
    }

  }

  /**
    * Starts the REST service and subscribes to/requests work
    *
    * It creates two subscriptions -- one for just running and executing, and another for executing and jobs
    * which just return the exit code.
    */
  def start(): Future[RunningService[ExecConfig, ExecutionRoutes]] = {
    import conf.serverImplicits._

    logger.info(s"Starting Execution Server in ${conf.location}")
    val startFuture = RunningService.start[ExecConfig, ExecutionRoutes](conf, restRoutes, executionRoutes)

    eventMonitor.accept(StartedSystem(conf.config))

    for {
      rs <- startFuture
      // only subscribe once the service has started
      ids <- conf.execSubscriptions.createSubscriptions(exchange)
    } yield {
      if (conf.healthUpdateFrequency.toMillis > 0) {
        HealthUpdate.schedule(exchange, ids.toSet, conf.healthUpdateFrequency)
      } else {
        logger.warn(s"Not scheduling health updates as healthUpdateFrequency is ${conf.healthUpdateFrequency}")
      }
      rs
    }
  }
}
