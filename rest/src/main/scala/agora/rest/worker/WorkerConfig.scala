package agora.rest
package worker

import java.util.concurrent.TimeUnit

import agora.api.exchange.Exchange
import agora.config._
import agora.rest.exchange.{ExchangeRestClient, ExchangeRoutes, ExchangeServerConfig}
import agora.rest.support.SupportRoutes
import agora.rest.swagger.SwaggerDocRoutes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future
import scala.concurrent.duration._

class WorkerConfig(c: Config) extends ServerConfig(c) {

  /** @return the initial amount of work to request from the exchange
    */
  def initialRequest = config.getInt("initialRequest")

  def startWorker(): Future[WorkerConfig.RunningWorker] = {
    val (exchange, optionalExchangeRoutes) = if (includeExchangeRoutes) {
      val localExchange            = exchangeConfig.newExchange
      val exRoutes: ExchangeRoutes = exchangeConfig.newExchangeRoutes(localExchange)
      (localExchange, Option(exRoutes.routes))
    } else {
      (exchangeClient, None)
    }

    val workerRoutes: DynamicWorkerRoutes = newWorkerRoutes(exchange)
    val restRoutes: Route                 = workerRoutes.routes ~ routes(optionalExchangeRoutes)

    RunningService.start(this, restRoutes, workerRoutes)
  }

  override def withFallback(fallback: Config): WorkerConfig = new WorkerConfig(config.withFallback(fallback))

  override def withOverrides(overrides: Config): WorkerConfig = new WorkerConfig(overrides).withFallback(config)

  def routes(exchangeRoutes: Option[Route]): Route = {
    def when(include: Boolean)(r: => Route): Stream[Route] = {
      if (include) Stream(r) else Stream.empty
    }

    val support = when(enableSupportRoutes)(SupportRoutes(config).routes)

    val swaggerRoutes: List[Route] = if (includeSwaggerRoutes) {
      val svc = SwaggerDocRoutes(location.resolveLocalhost.asHostPort, swaggerApiClasses)
      List(svc.routes, svc.site)
    } else {
      Nil
    }

    val all: Stream[Route] = exchangeRoutes.toStream ++ support ++ swaggerRoutes

    all.reduce(_ ~ _)
  }

  protected def swaggerApiClasses: Set[Class[_]] = {
    val baseSet = Set[Class[_]](classOf[SupportRoutes], classOf[DynamicWorkerRoutes])
    if (includeExchangeRoutes) {
      baseSet + classOf[ExchangeRoutes]
    } else {
      baseSet
    }
  }

  def includeExchangeRoutes = config.getBoolean("includeExchangeRoutes")

  /** @return exchange pointed at by this worker
    */
  lazy val exchangeConfig: ExchangeServerConfig = {
    new ExchangeServerConfig(config.getConfig("exchange"))
  }

  def newWorkerRoutes(exchange: Exchange): DynamicWorkerRoutes = {
    import serverImplicits.materializer
    DynamicWorkerRoutes(exchange, subscription, initialRequest)
  }

  def exchangeClient: ExchangeRestClient = defaultExchangeClient

  protected lazy val defaultExchangeClient: ExchangeRestClient = {
    exchangeConfig.client
  }

  lazy val subscriptionConfig = SubscriptionConfig(config.getConfig("subscription"))

  def subscription = subscriptionConfig.subscription(location)

  def unmarshalTimeout = config.getDuration("unmarshalTimeout", TimeUnit.MILLISECONDS).millis
}

object WorkerConfig {

  type RunningWorker = RunningService[WorkerConfig, DynamicWorkerRoutes]

  def apply(firstArg: String, theRest: String*): WorkerConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallbackConfig: Config = ConfigFactory.empty): WorkerConfig = {
    val wc = apply(configForArgs(args, fallbackConfig))
    wc.withFallback(load().config)
  }

  def load() = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config) = apply(config.getConfig("agora.worker").ensuring(!_.isEmpty))

  def apply(config: Config): WorkerConfig = new WorkerConfig(config)

  def unapply(config: WorkerConfig) = Option(config.config)

}
