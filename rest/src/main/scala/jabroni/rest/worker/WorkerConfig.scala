package jabroni.rest.worker

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe
import io.circe.parser._
import io.circe.{Json, ParsingFailure}
import jabroni.api.exchange.{Exchange, WorkSubscription}
import jabroni.api.json.JMatcher
import jabroni.api.worker.{HostLocation, WorkerDetails}
import jabroni.rest.client.ClientConfig
import jabroni.rest.exchange.ExchangeClient

import scala.util.Try

class WorkerConfig(config: Config) {
  val host = config.getString("host")
  val port = config.getInt("port")
  val location = HostLocation(host, port)
  val runUser: String = config.getString("runUser")

  def exchange: Exchange = {
    val restCC = ClientConfig(config.getConfig("exchange-client"))
    import restCC.implicits._
    ExchangeClient(restCC.restClient)
  }

  def workerDetails: Either[ParsingFailure, WorkerDetails] = {
    val wd = WorkerDetails(runUser, location)
    WorkerConfig.asJson(config.getConfig("details")).right.map { json =>
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

  def apply(config: Config = WorkerConfig.defaultConfig()): WorkerConfig = new WorkerConfig(config)

  def asJson(c: Config): Either[ParsingFailure, Json] = {
    val json = c.root.render(ConfigRenderOptions.concise().setJson(true))
    parse(json)
  }
}
