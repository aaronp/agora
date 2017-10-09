package agora.rest

import java.util.concurrent.atomic.AtomicInteger

import agora.api.exchange.{SelectionMode, SubmissionDetails}
import agora.api.json.JPredicate
import agora.api.worker.HostLocation
import agora.rest.client._
import agora.rest.worker.SubscriptionConfig
import akka.http.scaladsl.model.Uri
import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import io.circe.{Decoder, Json}

import scala.concurrent.duration._
import scala.util.Properties

class ClientConfig(config: Config) extends AutoCloseable {

  override def toString = {
    import agora.config.implicits._
    config.summary().mkString(s"ClientConfig with:\n ${cachedClients}\n\n", "\n\n", "")
  }

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

  /**
    * Createa a client which will send requests to the given locations.
    *
    * If the iterable contains a single entry, then the RestClient will be a whatever the cached client
    * returns for that location.
    *
    * If multiple locations are specified, then it will be a [[RoundRobinClient]] which sends requests in turn
    * to each location.
    *
    * @param locations
    * @return a [[RestClient]] for the given locations
    */
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
          val delay  = strategyConfig.getDuration("throttle-delay").toMillis.millis
          RetryStrategy.tolerate(nTimes).failuresWithin(within).withDelay(delay)
        case "throttled" =>
          val delay = strategyConfig.getDuration("throttle-delay").toMillis.millis
          RetryStrategy.throttle(delay)
        case other => sys.error(s"Unknown strategy failover strategy '$other'")
      }
    }
  }

  override def close(): Unit = {
    cachedClients.close()
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
    val matcher    = as[JPredicate]("matcher")
    val mode       = as[SelectionMode]("selectionMode")
    val orElse = config.getConfigList("orElse").asScala.toList.map { c =>
      val json = try {
        asJson(c.getConfig("match"))
      } catch {
        case _: ConfigException.WrongType =>
          Json.fromString(c.getString("match"))
      }
      cast[JPredicate](json, "orElse")
    }
    SubmissionDetails(Properties.userName, mode, awaitMatch, matcher, orElse).append(details)
  }
}
