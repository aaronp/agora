package jabroni.rest.worker

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe
import io.circe.parser._
import io.circe.{Json, ParsingFailure}
import jabroni.api.exchange.{Exchange, WorkSubscription}
import jabroni.api.json.JMatcher
import jabroni.api.worker.WorkerDetails
import jabroni.rest.ServerConfig
import jabroni.rest.client.ClientConfig
import jabroni.rest.exchange.ExchangeClient

import scala.util.Try

class WorkerConfig(serverConfig: ServerConfig) {

  def config = serverConfig.config

  def location = serverConfig.location

  def runUser = serverConfig.runUser


  def exchangeClientConfig = ClientConfig(serverConfig.config.getConfig("exchange"))

  def exchange: Exchange = {
    val restCC = exchangeClientConfig
    import restCC.implicits._
    ExchangeClient(restCC.restClient)
  }

  def workerDetails: Either[ParsingFailure, WorkerDetails] = {
    val detailsConf = config.getConfig("details")
    val name = detailsConf.getString("name")
    val id = detailsConf.getString("id")
    val wd = WorkerDetails(name, id, runUser, location)
    WorkerConfig.asJson(detailsConf).right.map { json =>
      wd.append(json)
    }
  }

  import WorkerConfig._

  def asMatcher(at: String) = {
    val fromConfig: Option[Either[circe.Error, JMatcher]] = Try(config.getConfig(at)).toOption.map { subConf =>
      asJson(subConf).right.flatMap(_.as[JMatcher])
    }

    val fromString = asJson(config).right.flatMap { json =>
      json.hcursor.downField(at).as[JMatcher]
    }

    fromConfig.getOrElse(fromString)
  }

  def subscription: Either[circe.Error, WorkSubscription] = {
    for {
      jm <- asMatcher("jobMatcher").right
      sm <- asMatcher("submissionMatcher").right
      wd <- workerDetails.right
    } yield {
      WorkSubscription(wd, jm, sm)
    }
  }
}

object WorkerConfig {
  def defaultConfig() = ConfigFactory.load().getConfig("jabroni.worker")

  def apply(config: ServerConfig = ServerConfig(defaultConfig())): WorkerConfig = new WorkerConfig(config)

  def asJson(c: Config): Either[ParsingFailure, Json] = {
    val json = c.root.render(ConfigRenderOptions.concise().setJson(true))
    parse(json)
  }
}
