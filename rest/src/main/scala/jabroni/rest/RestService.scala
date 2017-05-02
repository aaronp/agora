package jabroni.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import jabroni.rest.exchange.ExchangeRoutes
import jabroni.rest.ui.UIRoutes

/**
  * Main entry point for the rest service.
  */
object RestService extends Boot {

  def routeFromConf(conf: ServerConfig): Route = {
    import conf.implicits._

    val er: Route = ExchangeRoutes().routes
    val uiRoutes: Route = UIRoutes().routes

    er ~ uiRoutes
  }
}
