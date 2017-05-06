package jabroni.rest.worker

import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import io.circe
import jabroni.api.exchange.{Exchange, WorkSubscription}
import jabroni.api.json.JMatcher
import jabroni.api.worker.WorkerDetails
import jabroni.rest.client.ClientConfig
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.ui.UIRoutes
import jabroni.rest.{Boot, RunningService, ServerConfig}
import akka.http.scaladsl.server.Directives._

import scala.concurrent.Future
import scala.util.Try

case class WorkerConfig(override val config: Config) extends ServerConfig {
  override type Me = WorkerConfig
  override def self = this
  def initialRequest = config.getInt("initialRequest")

  import WorkerConfig._

  def startWorker(): Future[RunningWorker] = {
    val f: Future[RunningService[WorkerConfig, WorkerRoutes]] = runWithRoutes(routes, workerRoutes)
    f
  }

  def routes: Route = {
    if (includeUIRoutes) {
      val uiRoutes: Route = UIRoutes("ui/worker.html").routes
      workerRoutes.routes ~ uiRoutes
    } else {
      workerRoutes.routes
    }
  }

  /**
    * @return a client configuration which will talk to an exchange
    */
  def exchangeClientConfig = ClientConfig(config.getConfig("exchange"))

  lazy val workerRoutes: WorkerRoutes = {
    exchangeClientConfig.workerRoutes(subscription, initialRequest)
  }

  lazy val exchange: Exchange = {
    val restCC = exchangeClientConfig
    import restCC.implicits._
    ExchangeClient(restCC.restClient)
  }

  def workerDetails: WorkerDetails = {
    val detailsConf = config.getConfig("details")
    val name = detailsConf.getString("name")
    val id = detailsConf.getString("id")
    WorkerDetails(name, id, runUser, location).append(Boot.asJson(detailsConf))
  }

  def asMatcher(at: String): Either[circe.Error, JMatcher] = {
    val fromConfig: Option[Either[circe.Error, JMatcher]] = Try(config.getConfig(at)).toOption.map { subConf =>
      Boot.asJson(subConf).as[JMatcher]
    }

    val fromString = Boot.asJson(config).hcursor.downField(at).as[JMatcher]

    fromConfig.getOrElse(fromString)
  }

  def subscription: WorkSubscription = subscriptionEither.right.get

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

  def baseConfig(): Config = ConfigFactory.load("worker.conf")

  def defaultConfig(): Config = baseConfig.resolve

  //  def apply(config: Config): WorkerConfig = new WorkerConfig(config)

  def apply(args: Array[String] = Array.empty, defaultConfig: Config = defaultConfig): WorkerConfig = {
    WorkerConfig(Boot.configForArgs(args, defaultConfig))
  }

}
