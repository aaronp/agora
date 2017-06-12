package jabroni.rest
package worker

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import io.circe
import jabroni.api.exchange.{Exchange, MatchObserver, WorkSubscription}
import jabroni.api.json.JMatcher
import jabroni.api.worker.WorkerDetails
import jabroni.rest.exchange.{ExchangeClient, ExchangeConfig, ExchangeRoutes}
import jabroni.rest.ui.{SupportRoutes, UIRoutes}

import scala.concurrent.Future
import scala.util.Try

class WorkerConfig(c: Config) extends ServerConfig(c) {

  /** @return the initial amount of work to request from the exchange
    */
  def initialRequest = config.getInt("initialRequest")

  def startWorker(): Future[WorkerConfig.RunningWorker] = {

    import serverImplicits._
    val (exchange, optionalExchangeRoutes) = if (includeExchangeRoutes) {
      val obs = MatchObserver()
      val localExchange = exchangeConfig.newExchange(obs)
      val exRoutes: ExchangeRoutes = exchangeConfig.newExchangeRoutes(localExchange)
      (localExchange, Option(exRoutes.routes))
    } else {
      (exchangeClient, None)
    }

    val workerRoutes: WorkerRoutes = newWorkerRoutes(exchange)
    val restRoutes: Route = routes(workerRoutes, optionalExchangeRoutes)

    RunningService.start(this, restRoutes, workerRoutes)
  }

  def withFallback[C <: WorkerConfig](fallback: C): WorkerConfig = new WorkerConfig(config.withFallback(fallback.config))


  def landingPage = "ui/test.html"

  def routes(workerRoutes: WorkerRoutes, exchangeRoutes: Option[Route]): Route = {
    import serverImplicits._
    def when(include: Boolean)(r: => Route): Stream[Route] = {
      if (include) Stream(r) else Stream.empty
    }

    val support = when(enableSupportRoutes)(SupportRoutes(config).routes)
    val ui = when(includeUIRoutes)(UIRoutes(landingPage).routes)

    val all = Stream(workerRoutes.routes) ++ exchangeRoutes.toStream ++ support ++ ui
    all.reduce(_ ~ _)
  }

  def includeExchangeRoutes = config.getBoolean("includeExchangeRoutes")

  /** @return exchange pointed at by this worker
    */
  lazy val exchangeConfig: ExchangeConfig = {
    new ExchangeConfig(config.getConfig("exchange"))
  }

  def newWorkerRoutes(exchange: Exchange): WorkerRoutes = {
    import serverImplicits.materializer
    WorkerRoutes(exchange, subscription, initialRequest)
  }

  def exchangeClient: ExchangeClient = defaultExchangeClient

  protected lazy val defaultExchangeClient: ExchangeClient = {
    if (includeExchangeRoutes) {
      ExchangeClient(restClient) { workerLocation =>
        val restClient = retryClient(workerLocation)
        WorkerClient(restClient)
      }
    } else {
      exchangeConfig.client
    }
  }

  def workerDetails: WorkerDetails = {
    val detailsConf = config.getConfig("details")
    val name = detailsConf.getString("name")
    val id = detailsConf.getString("id")
    val path = detailsConf.getString("path")
    WorkerDetails(name, id, runUser, path, location).append(asJson(detailsConf))
  }

  def asMatcher(at: String): Either[circe.Error, JMatcher] = {
    val fromConfig: Option[Either[circe.Error, JMatcher]] = Try(config.getConfig(at)).toOption.map { subConf =>
      asJson(subConf).as[JMatcher]
    }

    val fromString = asJson(config).hcursor.downField(at).as[JMatcher]

    fromConfig.getOrElse(fromString)
  }

  def subscription: WorkSubscription = subscriptionEither match {
    case Left(err) => sys.error(s"Couldn't parse the config as a subscription: $err")
    case Right(s) => s
  }

  def subscriptionEither: Either[circe.Error, WorkSubscription] = {
    for {
      jm <- asMatcher("jobMatcher").right
      sm <- asMatcher("submissionMatcher").right
    } yield {
      WorkSubscription(workerDetails, jm, sm)
    }
  }
}

object WorkerConfig {

  type RunningWorker = RunningService[WorkerConfig, WorkerRoutes]

  def apply(firstArg: String, theRest: String*): WorkerConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallbackConfig: Config = ConfigFactory.empty): WorkerConfig = {
    val wc = apply(configForArgs(args, fallbackConfig))
    wc.withFallback(load())
  }

  def load() = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config) = apply(config.getConfig("jabroni.worker").ensuring(!_.isEmpty))

  def apply(config: Config): WorkerConfig = new WorkerConfig(config)

  def unapply(config: WorkerConfig) = Option(config.config)

}
