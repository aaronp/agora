package jabroni.rest.worker

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import jabroni.api.exchange.Exchange
import jabroni.rest.ui.UIRoutes
import jabroni.rest.{Boot, ServerConfig}

import scala.concurrent.Future

/**
  * Main entry point for the rest service.
  */
object Worker extends Boot {

  override def defaultConfig = ServerConfig.defaultConfig("jabroni.worker")

  override type Service = WorkerConfig

  override def serviceFromConf(conf: ServerConfig) = WorkerConfig(conf)

  def runWith(routes: WorkerRoutes, config: ServerConfig = ServerConfig(WorkerConfig.defaultConfig())): Future[Worker.RunningService] = {
    val workerConf = WorkerConfig(config)
    start(routes.routes, workerConf, config)
  }

  override def routeFromService(conf: ServerConfig, workerConf: WorkerConfig): Future[Route] = {
    import conf.implicits._

    workerConf.subscription match {
      case Left(err) => Future.failed(err)
      case Right(baseSubscription) =>

        //        val newSub = baseSubscription.copy(jobMatcher = baseSubscription.jobMatcher.and(RunProcess.matcher))
        //        val exchange: Exchange = workerConf.exchange
        //
        //        val r = WorkerRoutes().routes ~ UIRoutes().routes
        //        Future.successful(r)
        ???
    }
  }

}
