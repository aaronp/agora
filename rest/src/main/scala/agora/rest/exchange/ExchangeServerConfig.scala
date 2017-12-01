package agora.rest.exchange

import agora.api.exchange.ServerSideExchange
import agora.config._
import agora.rest.RunningService
import agora.rest.ui.UIRoutes
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future

class ExchangeServerConfig(c: Config) extends ExchangeConfig(c) {

  def start(exchange: ServerSideExchange = newExchange): Future[ExchangeServerConfig.RunningExchange] = {
    val er = newExchangeRoutes(exchange)

    RunningService.start(this, routes(er), er)
  }

  def uiRoutes: Option[UIRoutes] = UIRoutes.unapply(this)

  def newExchangeRoutes(exchange: ServerSideExchange): ExchangeRoutes = ExchangeRoutes(exchange)

  def routes(exchangeRoutes: ExchangeRoutes): Route = {
    val base = exchangeRoutes.routes
    uiRoutes.fold(base) { r =>
      import akka.http.scaladsl.server.Directives._

      base ~ r.routes
    }
  }

  override def withFallback(fallback: ExchangeConfig) = new ExchangeServerConfig(config.withFallback(fallback.config))

}

object ExchangeServerConfig {
  type RunningExchange = RunningService[ExchangeServerConfig, ExchangeRoutes]

  def apply(firstArg: String, theRest: String*): ExchangeServerConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallback: Config = ConfigFactory.empty): ExchangeServerConfig = {
    val ex: ExchangeServerConfig = apply(configForArgs(args, fallback))
    ex.withFallback(load())
  }

  def apply(config: Config): ExchangeServerConfig = new ExchangeServerConfig(config)

  def load() = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config) = apply(config.getConfig("agora.exchange").ensuring(!_.isEmpty))
}
