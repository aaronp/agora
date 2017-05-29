package jabroni.rest
package exchange

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import jabroni.rest.ui.UIRoutes

import scala.concurrent.Future

object ExchangeConfig {

  def apply(firstArg: String, theRest: String*): ExchangeConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallback: Config = ConfigFactory.empty): ExchangeConfig = {
    val ex = apply(configForArgs(args, fallback))
    ex.withFallback(load())
  }

  def apply(config: Config): ExchangeConfig = new ExchangeConfig(config)

  def load() = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config) = apply(config.getConfig("jabroni.exchange").ensuring(!_.isEmpty))

  type RunningExchange = RunningService[ExchangeConfig, ExchangeRoutes]
}

class ExchangeConfig(c: Config) extends ServerConfig(c) {

  def start(): Future[ExchangeConfig.RunningExchange] = {
    RunningService.start(this, routes, exchangeRoutes)
  }

  def withFallback(fallback: ExchangeConfig) = new ExchangeConfig(config.withFallback(fallback.config))

  def client: ExchangeClient = defaultClient
  protected  lazy val defaultClient: ExchangeClient = {
    import implicits._
    ExchangeClient(restClient)
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
