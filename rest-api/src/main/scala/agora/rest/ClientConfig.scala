package agora.rest

import java.util.concurrent.atomic.AtomicInteger
import javax.naming.ConfigurationException

import agora.api.exchange.{SelectionMode, SubmissionDetails}
import agora.api.json.JMatcher
import agora.api.worker.HostLocation
import agora.rest.client._
import agora.rest.worker.SubscriptionConfig
import akka.http.scaladsl.model.Uri
import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import io.circe.{Decoder, Json}

import scala.concurrent.duration._
import scala.util.Properties

class ClientConfig(config: Config) {

  import ClientConfig._

  def actorSystemName: String = config.getString("actorSystemName")

  def host = config.getString("host") match {
    case "0.0.0.0" => "localhost"
    case h         => h
  }

  def port = config.getInt("port")

  def location = HostLocation(host, port)

  def submissionDetails: SubmissionDetails = submissionDetailsFromConfig(config.getConfig("submissionDetails"))

  /** A means of accessing reusable clients. */
  lazy val cachedClients: CachedClient = CachedClient { loc: HostLocation =>
    retryClient(loc)
  }

  def clientFor(location: HostLocation, theRest: HostLocation*): RestClient = {
    clientFor(location :: theRest.toList)
  }

  def clientFor(locations: Iterable[HostLocation]): RestClient = {
    if (locations.size == 1) {
      cachedClients(locations.head)
    } else {
      val clients = locations.map(cachedClients.apply)
      RoundRobinClient(clients, clientFailover.strategy)
    }
  }

  private[this] val uniqueActorNameCounter = new AtomicInteger(0)

  def nextActorSystemName() = uniqueActorNameCounter.getAndAdd(1) match {
    case 0 => actorSystemName
    case n => s"$actorSystemName-$n"
  }

  def clientForUri(uri: Uri): RestClient = clientFor(asLocation(uri))

  def restClient: RestClient = clientFor(location)

  protected def retryClient(loc: HostLocation = location): RetryClient = {
    RetryClient(clientFailover.strategy)(() => newRestClient(loc))
  }

  private def newRestClient(inputLoc: HostLocation, name: String = nextActorSystemName()): RestClient = {
    val loc = inputLoc.host match {
      case "0.0.0.0" => inputLoc.copy(host = "localhost")
      case _         => inputLoc
    }
    RestClient(loc, () => newSystem(name).materializer)
  }

  def newSystem(name: String = nextActorSystemName()) = new AkkaImplicits(name, config)

  object clientFailover {

    val strategyConfig = config.getConfig("clientFailover")

    def strategy = {
      strategyConfig.getString("strategy") match {
        case "limiting" =>
          val nTimes = strategyConfig.getInt("nTimes")
          val within = strategyConfig.getDuration("within").toMillis.millis
          RetryStrategy.tolerate(nTimes).failuresWithin(within)
        case "throttled" =>
          val delay = strategyConfig.getDuration("throttle-delay").toMillis.millis
          RetryStrategy.throttle(delay)
        case other => sys.error(s"Unknown strategy failover strategy '$other'")
      }
    }
  }

}

object ClientConfig {

  def load() = new ClientConfig(ConfigFactory.load("client.conf"))

  def asLocation(uri: Uri): HostLocation = {
    val addresses = uri.authority.host.inetAddresses.toList
    val hostName  = addresses.head.getHostName
    HostLocation(hostName, uri.authority.port)
  }

  def submissionDetailsFromConfig(config: Config): SubmissionDetails = {
    import SubscriptionConfig.asJson

    import scala.collection.JavaConverters._

    def as[T: Decoder](key: String): T = {
      val json = try {
        asJson(config.getConfig(key))
      } catch {
        case _: ConfigException.WrongType =>
          Json.fromString(config.getString(key))
      }

      cast(json, key)
    }

    def cast[T: Decoder](json: Json, key: String): T = {
      json.as[T] match {
        case Right(m) => m
        case Left(err) => {
          sys.error(s"Couldn't parse the client subscription '$key' $json : $err")
        }
      }
    }

    val details    = asJson(config.getConfig("details"))
    val awaitMatch = config.getBoolean("awaitMatch")
    val matcher    = as[JMatcher]("matcher")
    val mode       = as[SelectionMode]("selectionMode")
    val orElse = config.getConfigList("orElse").asScala.toList.map { c =>
      val json = try {
        asJson(c.getConfig("match"))
      } catch {
        case _: ConfigException.WrongType =>
          Json.fromString(c.getString("match"))
      }
      cast[JMatcher](json, "orElse")
    }
    SubmissionDetails(Properties.userName, mode, awaitMatch, matcher, orElse).append(details)
  }
}
