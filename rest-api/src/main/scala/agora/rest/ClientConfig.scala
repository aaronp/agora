package agora.rest

import java.util.concurrent.atomic.AtomicInteger

import agora.api.worker.HostLocation
import agora.rest.client._
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config

import scala.concurrent.duration._

class ClientConfig(config: Config) {

  def actorSystemName: String = config.getString("actorSystemName")

  def host = config.getString("host") match {
    case "0.0.0.0" => "localhost"
    case h         => h
  }

  def port = config.getInt("port")

  def location = HostLocation(host, port)

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
