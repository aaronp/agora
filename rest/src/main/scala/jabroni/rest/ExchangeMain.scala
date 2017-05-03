package jabroni.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import jabroni.rest.exchange.ExchangeRoutes
import jabroni.rest.ui.UIRoutes
import jabroni.rest.worker.MatchDispatcher

import scala.concurrent.Future

/**
  * Main entry point for the rest service.
  */
object ExchangeMain extends Boot {

  override def defaultConfig = ServerConfig.defaultConfig("jabroni.exchange")

  def routeFromConf(conf: ServerConfig) = {
    import conf.implicits._

    val er = ExchangeRoutes()
    er.observer +=[Unit, MatchDispatcher] (new MatchDispatcher)
    val uiRoutes: Route = UIRoutes().routes

    Future(er.routes ~ uiRoutes)
  }
}
