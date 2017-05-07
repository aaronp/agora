package jabroni.rest.exchange

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import jabroni.rest.ui.UIRoutes
import jabroni.rest.{Boot, RunningService, ServerConfig}

object ExchangeConfig {
  def baseConfig = ConfigFactory.load("exchange.conf")

  def defaultConfig = baseConfig.resolve

  def apply(args: Array[String] = Array.empty, defaultConfig: Config = defaultConfig): ExchangeConfig = {
    ExchangeConfig(Boot.configForArgs(args, defaultConfig))
  }

  type RunningExchange = RunningService[ExchangeConfig, ExchangeRoutes]
}

case class ExchangeConfig(override val config: Config) extends ServerConfig {
  override type Me = ExchangeConfig
  override def self : Me = this

  def startExchange() = runWithRoutes("Exchange", routes, exchangeRoutes)

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
