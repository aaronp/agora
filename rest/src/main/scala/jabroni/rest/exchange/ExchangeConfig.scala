package jabroni.rest
package exchange

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import jabroni.api.exchange.{Exchange, JobPredicate, MatchObserver, ServerSideExchange}
import jabroni.api.worker.HostLocation
import jabroni.rest.ui.UIRoutes
import jabroni.rest.worker.WorkerClient

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

  def start(exchange : ServerSideExchange = newExchange(MatchObserver())): Future[ExchangeConfig.RunningExchange] = {
    val er = newExchangeRoutes(exchange)
    RunningService.start(this, routes(er), er)
  }

  def withFallback(fallback: ExchangeConfig) = new ExchangeConfig(config.withFallback(fallback.config))

  def client: ExchangeClient = {
    ExchangeClient(restClient) { (workerLocation: HostLocation) =>
      val restClient = retryClient(workerLocation)
      WorkerClient(restClient)
    }
  }

  def newExchange(obs: MatchObserver)(implicit matcher: JobPredicate = JobPredicate()): ServerSideExchange = {
    val underlying: Exchange = Exchange(obs)(matcher)
    val safeExchange: Exchange = ActorExchange(underlying, serverImplicits.system)
    new ServerSideExchange(safeExchange, obs)
  }

  def newExchangeRoutes(exchange: ServerSideExchange): ExchangeRoutes = {
    ExchangeRoutes(exchange)(serverImplicits.materializer)
  }

  def routes(exchangeRoutes: ExchangeRoutes): Route = {
    if (includeUIRoutes) {
      val uiRoutes: Route = UIRoutes("ui/index.html").routes
      exchangeRoutes.routes ~ uiRoutes
    } else {
      exchangeRoutes.routes
    }
  }
}
