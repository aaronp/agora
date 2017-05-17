package jabroni.rest
package exchange

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import jabroni.rest.client.RestClient
import jabroni.rest.ui.UIRoutes

object ExchangeConfig {
  def baseConfig = ConfigFactory.load("exchange.conf")

  def defaultConfig = baseConfig.resolve

  def apply(firstArg: String, theRest: String*): ExchangeConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, defaultConfig: Config = defaultConfig): ExchangeConfig = {
    apply(configForArgs(args, defaultConfig))
  }
  def apply(config: Config): ExchangeConfig = {
    new BaseConfig(config.resolve) with ExchangeConfig
  }

  type RunningExchange = RunningService[ExchangeConfig, ExchangeRoutes]
}

trait ExchangeConfig extends ServerConfig {
  override type Me = ExchangeConfig

  override def self: Me = this

  def startExchange() = runWithRoutes("Exchange", routes, exchangeRoutes)

  lazy val client: ExchangeClient = {
    import implicits._
    ExchangeClient(RestClient(location))
  }

  lazy val exchangeRoutes: ExchangeRoutes = {
    import implicits._
    ExchangeRoutes()
  }

  def routes: Route = {
    if (includeUIRoutes) {
      val uiRoutes: Route = UIRoutes("ui/index.html").routes
      exchangeRoutes.routes ~ uiRoutes
    } else {
      exchangeRoutes.routes
    }
  }
}
