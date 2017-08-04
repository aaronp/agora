package agora.rest
package worker

import java.util.concurrent.TimeUnit

import agora.api.exchange.{Exchange, WorkSubscription}
import agora.api.json.JMatcher
import agora.api.worker.{HostLocation, WorkerDetails}
import agora.rest.exchange.{ExchangeClient, ExchangeConfig, ExchangeRoutes}
import agora.rest.support.SupportRoutes
import agora.rest.ui.UIRoutes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import io.circe

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

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

  def landingPage = "ui/test.html"

  def routes(exchangeRoutes: Option[Route]): Route = {
    def when(include: Boolean)(r: => Route): Stream[Route] = {
      if (include) Stream(r) else Stream.empty
    }

    val support = when(enableSupportRoutes)(SupportRoutes(config).routes)
    val ui      = when(includeUIRoutes)(UIRoutes(landingPage).routes)

    val all = exchangeRoutes.toStream ++ support ++ ui
    all.reduce(_ ~ _)
  }

  def includeExchangeRoutes = config.getBoolean("includeExchangeRoutes")

  /** @return exchange pointed at by this worker
    */
  lazy val exchangeConfig: ExchangeConfig = {
    new ExchangeConfig(config.getConfig("exchange"))
  }

  def newWorkerRoutes(exchange: Exchange): DynamicWorkerRoutes = {
    import serverImplicits.materializer
    DynamicWorkerRoutes(exchange, subscription, initialRequest)
  }

  def exchangeClient: ExchangeClient = defaultExchangeClient

  protected lazy val defaultExchangeClient: ExchangeClient = {

    if (includeExchangeRoutes) {
      ExchangeClient(restClient) { (workerLocation: HostLocation) =>
        WorkerClient(clientFor(workerLocation))
      }
    } else {
      exchangeConfig.client
    }
  }

  def workerDetails: WorkerDetails = {
    val detailsConf = config.getConfig("details")
    val name        = detailsConf.getString("name")
    val id          = detailsConf.getString("id")
    val path        = detailsConf.getString("path")
    WorkerDetails(name, id, runUser, path, location).append(asJson(detailsConf))
  }

  def asMatcher(at: String): Either[circe.Error, JMatcher] = {
    val fromConfig: Option[Either[circe.Error, JMatcher]] = Try(config.getConfig(at)).toOption.map { subConf =>
      val json = asJson(subConf)
      json.as[JMatcher]
    }

    val fromString = asJson(config).hcursor.downField(at).as[JMatcher]

    fromConfig.getOrElse(fromString)
  }

  def subscription: WorkSubscription = subscriptionEither match {
    case Left(err) => sys.error(s"Couldn't parse the config as a subscription: $err")
    case Right(s)  => s
  }

  def subscriptionEither: Either[circe.Error, WorkSubscription] = {
    for {
      jm <- asMatcher("jobMatcher").right
      sm <- asMatcher("submissionMatcher").right
    } yield {
      WorkSubscription(workerDetails, jm, sm)
    }
  }

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
