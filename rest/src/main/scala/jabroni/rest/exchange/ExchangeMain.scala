package jabroni.rest.exchange

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import jabroni.rest.ui.UIRoutes
import jabroni.rest.{Boot, ServerConfig}

import scala.concurrent.Future

/** ExchangeMain
  * Main entry point for the rest service.
  */
object ExchangeMain extends Boot {

  override def defaultConfig = ServerConfig.defaultConfig("jabroni.exchange")

  override type Service = ExchangeRoutes

  override def serviceFromConf(conf: ServerConfig): Service = {
    import conf.implicits._
    val er = ExchangeRoutes()
    //    er.observer +=[Unit, MatchDispatcher] (new MatchDispatcher)
    er
  }


  override def routeFromService(conf: ServerConfig, svc: ExchangeRoutes): Future[Route] = {
    val uiRoutes: Route = UIRoutes("ui/index.html").routes
    Future.successful(svc.routes ~ uiRoutes)
  }

}
