package agora.exec

import agora.api.exchange.{Exchange, ServerSideExchange}
import agora.exec.events.{DeleteBefore, Housekeeping, StartedSystem, SystemEventMonitor}
import agora.exec.rest.{ExecutionRoutes, ExecutionWorkflow, QueryRoutes, UploadRoutes}
import agora.exec.workspace.{UpdatingWorkspaceClient, WorkspaceClient}
import agora.health.HealthUpdate
import agora.rest.RunningService
import agora.rest.exchange.ExchangeRoutes
import agora.rest.worker.SubscriptionConfig
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
    /**
      * Should we connect to another exchange or this one?
      */
    val (exchange: Exchange, optionalExchangeRoutes: Option[Route]) = if (includeExchangeRoutes) {

      val localExchange: ServerSideExchange = exchangeConfig.newExchange
      val er: ExchangeRoutes                = exchangeConfig.newExchangeRoutes(localExchange)

      localExchange -> Option(er.routes)
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

  lazy val housekeeping = Housekeeping.every(conf.housekeeping.checkEvery)(conf.serverImplicits.system)

  lazy val workspaceClient: WorkspaceClient = conf.workspaceClient

  def workflow: ExecutionWorkflow =
    ExecutionWorkflow(conf.defaultEnv, workspaceClient, conf.eventMonitor, conf.enableCache)

  /** @return a future of the ExecutionRoutes once the exec subscription completes
    */
  lazy val executionRoutes: ExecutionRoutes = {
    new ExecutionRoutes(conf, exchange, workflow)
  }

  def uploadRoutes(workspace: WorkspaceClient = workspaceClient) = UploadRoutes(workspace)

  lazy val eventMonitor: SystemEventMonitor = {
    val monitor = conf.eventMonitor

    val windowInNanos = conf.housekeeping.removeEventsOlderThan.toNanos
    housekeeping.registerHousekeepingEvent { () =>
      val timestamp = {
        agora.api.time.now().minusNanos(windowInNanos)
      }
      monitor.accept(DeleteBefore(timestamp))
    }
    monitor
  }

  def queryRoutes: Route = QueryRoutes(eventMonitor).routes(conf.enableSupportRoutes)

  def restRoutes(uploadRoutes: UploadRoutes): Route = {

    val corsSettings = {
      val default = CorsSettings.defaultSettings
      default.copy(allowedMethods = default.allowedMethods ++ List(HttpMethods.PUT, HttpMethods.DELETE))
    }
    val baseRoutes = cors(corsSettings)(
      uploadRoutes.routes ~ executionRoutes.routes(optionalExchangeRoutes)
    )

    if (conf.eventMonitorConfig.enabled) {
      baseRoutes ~ queryRoutes
    } else {
      baseRoutes
    }

  }

  lazy val updatingClient: UpdatingWorkspaceClient = {
    import conf.serverImplicits._
    UpdatingWorkspaceClient(workspaceClient, exchange)
  }
  lazy val uploadRoutes: UploadRoutes = UploadRoutes(updatingClient)

  /**
    * Starts the REST service and subscribes to/requests work
    *
    * It creates two subscriptions -- one for just running and executing, and another for executing and jobs
    * which just return the exit code.
    */
  def start(): Future[RunningService[ExecConfig, ExecBoot]] = {

    logger.info(s"Starting Execution Server in ${conf.location}")

    val startFuture = RunningService.start[ExecConfig, ExecBoot](conf, restRoutes(uploadRoutes), this)

    import agora.api.config.JsonConfig.implicits._
    import conf.serverImplicits._

    eventMonitor.accept(StartedSystem(conf.config.configAsJson))

    for {
      rs <- startFuture

      resolvedLocation = conf.hostResolver.resolveHostname(rs.localAddress)

      // only subscribe once the service has started
      ids <- conf.execSubscriptions(resolvedLocation).createSubscriptions(exchange)
    } yield {

      // we now have a work subscription -- have our upload routes update it
      // when workspaces are created/closed
      updatingClient.addSubscriptions(ids)

      if (conf.healthUpdateFrequency.toMillis > 0) {
        HealthUpdate.schedule(exchange, ids.toSet, conf.healthUpdateFrequency)
      } else {
        logger.warn(s"Not scheduling health updates as healthUpdateFrequency is ${conf.healthUpdateFrequency}")
      }

      rs
    }
  }
}
