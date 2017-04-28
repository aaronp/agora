package jabroni.rest

import akka.http.scaladsl.server.Route
import jabroni.api.exchange.Exchange
import jabroni.rest.exchange.ExchangeRoutes

/**
  * Main entry point for the rest service.
  */
object RestService extends Boot {

  def routeFromConf(conf: ServerConfig): Route = {
    import conf.implicits._
    val exchange: Exchange = Exchange()
    ExchangeRoutes(exchange).routes
  }
}
