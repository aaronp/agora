package jabroni.rest
package worker

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe
import jabroni.api.exchange.WorkSubscription
import jabroni.api.json.JMatcher
import jabroni.api.worker.WorkerDetails
import jabroni.rest.client.RestClient
import jabroni.rest.exchange.{ExchangeClient, ExchangeConfig}
import jabroni.rest.ui.UIRoutes

import scala.concurrent.Future
import scala.util.Try

trait WorkerConfig extends ServerConfig {
  override type Me = WorkerConfig

  override def self = this

  override def toString = {
    import jabroni.domain.RichConfig.implicits._

    config.withPaths("port",
      "host",
      "details",
      "submissionMatcher",
      "jobMatcher",
      "subscription",
      "exchange").root.render(ConfigRenderOptions.concise().setFormatted(true))
  }

  /** @return the initial amount of work to request from the exchange
    */
  def initialRequest = config.getInt("initialRequest")

  import WorkerConfig._

  def startWorker(): Future[RunningWorker] = {
    runWithRoutes("Worker", routes, workerRoutes)
  }

  def includeExchangeRoutes = config.getBoolean("includeExchangeRoutes")

  def routes: Route = {
    val withUI = if (includeUIRoutes) {
      val uiRoutes: Route = UIRoutes("ui/worker.html").routes
      workerRoutes.routes ~ uiRoutes
    } else {
      workerRoutes.routes
    }
    if (includeExchangeRoutes) {
      withUI ~ exchangeConfig.exchangeRoutes.routes
    } else {
      withUI
    }
  }

  /** @return exchange pointed at by this worker
    */
  lazy val exchangeConfig = ExchangeConfig(config.getConfig("exchange"))

  lazy val workerRoutes: WorkerRoutes = {
    import implicits._

    val exchange = if (includeExchangeRoutes) {
      exchangeConfig.exchangeRoutes.exchange
    } else {
      exchangeClient
    }
    WorkerRoutes(exchange, subscription, initialRequest)
  }

  lazy val exchangeClient: ExchangeClient = {
    import implicits._
    if (includeExchangeRoutes) {
      ExchangeClient(location)
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

  def baseConfig(): Config = ConfigFactory.parseResourcesAnySyntax("worker")

  def defaultConfig(): Config = baseConfig.resolve

  def apply(firstArg: String, theRest: String*): WorkerConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, defaultConfig: Config = baseConfig): WorkerConfig = {
    apply(configForArgs(args, defaultConfig))
  }

  def apply(config: Config): WorkerConfig = new BaseConfig(config.resolve) with WorkerConfig

}
