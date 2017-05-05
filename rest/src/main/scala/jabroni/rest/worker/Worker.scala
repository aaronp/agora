package jabroni.rest.worker

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import jabroni.rest.ui.UIRoutes
import jabroni.rest.{Boot, ServerConfig}

import scala.concurrent.Future

/**
  * Main entry point for the rest service.
  */
object Worker extends Boot {

  override type Service = WorkerConfig


  override def defaultConfig = ServerConfig.defaultConfig("jabroni.worker")

  override def serviceFromConf(conf: ServerConfig) = WorkerConfig(conf)

  override def routeFromService(conf: ServerConfig, workerConf: WorkerConfig): Future[Route] = {
    val wr: WorkerRoutes = workerConf.workerRoutes

    val uiRoutes: Route = UIRoutes("ui/worker.html").routes
    Future.successful(wr.routes ~ uiRoutes)
  }
}
