package jabroni.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import jabroni.api.exchange.WorkSubscriptionAck
import jabroni.api.worker.execute.RunProcess
import jabroni.rest.ui.UIRoutes
import jabroni.rest.worker.{Handler, WorkerConfig, WorkerRoutes}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Main entry point for the rest service.
  */
object WorkerMain extends Boot {

  override def defaultConfig = ServerConfig.defaultConfig("jabroni.worker")

  def routeFromConf(conf: ServerConfig) = {
    import conf.implicits._

    routeFromWorkerConf(WorkerConfig(conf))
  }

  def routeFromWorkerConf(workerConf: WorkerConfig)(implicit ec: ExecutionContext) = {
    workerConf.subscription match {
      case Left(err) => Future.failed(err)
      case Right(baseSubscription) =>

        val newSub = baseSubscription.copy(jobMatcher = baseSubscription.jobMatcher.and(RunProcess.matcher))
        val exchange = workerConf.exchange

        val resp: Future[WorkSubscriptionAck] = exchange.subscribe(newSub)

        resp.map {
          case WorkSubscriptionAck(key) =>
            val onExec = Handler.execute(exchange, key)
            val onWebSocketRequest = Handler.webSocketRequest(exchange, key)
            val handlers: Map[String, Handler] = Map(
              "ui" -> onWebSocketRequest,
              "exec" -> onExec
            )
            val er: Route = WorkerRoutes(handlers).routes
            val uiRoutes: Route = UIRoutes().routes
            er ~ uiRoutes
        }
    }
  }
}
