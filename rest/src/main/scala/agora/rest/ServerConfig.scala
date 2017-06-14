package agora.rest

import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import agora.api.worker.HostLocation
import agora.domain.RichConfigOps
import agora.rest.client.{CachedClient, RestClient, RetryClient, RetryStrategy}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * A parsed configuration for our agora app
  */
class ServerConfig(val config: Config) extends RichConfigOps {

  def actorSystemName: String = config.getString("actorSystemName")

  def host = config.getString("host")

  def port = config.getInt("port")

  def launchBrowser = config.getBoolean("launchBrowser")

  def waitOnUserInput = config.getBoolean("waitOnUserInput")

  def runUser = config.getString("runUser")

  def includeUIRoutes = config.getBoolean("includeUIRoutes")

  def enableSupportRoutes = config.getBoolean("enableSupportRoutes")

  def chunkSize = config.getInt("chunkSize")

  def location = HostLocation(host, port)

  def hostLocationForConfig(c: Config) = {
    HostLocation(c.getString("host"), c.getInt("port"))
  }

  object clientFailover {

    val strategyConfig = config.getConfig("clientFailover")
    // ThrottleStrategy

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

  private[this] val uniqueActorNameCounter = new AtomicInteger(0)

  def nextActorSystemName() = uniqueActorNameCounter.getAndAdd(1) match {
    case 0 => actorSystemName
    case n => s"$actorSystemName-$n"
  }

  lazy val serverImplicits = newSystem(s"${actorSystemName}-server")

  def newSystem(name: String = nextActorSystemName) = new AkkaImplicits(name)

  /** A means of accessing reusable clients. */
  lazy val clientFor = CachedClient { loc: HostLocation =>
    retryClient(loc)
  }

  def clientForUri(uri: Uri): RestClient = clientFor(asLocation(uri))

  def restClient: RestClient = clientFor(location)

  def asLocation(uri: Uri): HostLocation = {
    val p         = uri.path
    val addresses = uri.authority.host.inetAddresses.toList
    val hostName  = addresses.head.getHostName
    HostLocation(hostName, uri.authority.port)
  }


  protected def retryClient(loc: HostLocation = location): RetryClient = {
    RetryClient(clientFailover.strategy)(() => newRestClient(loc))
  }

  private def newRestClient(loc: HostLocation): RestClient = {
    RestClient(loc, () => (newSystem()).materializer)
  }

  def runWithRoutes[T](routes: Route, svc: T): Future[RunningService[ServerConfig, T]] = {
    RunningService.start(this, routes, svc)
  }

  override def toString = config.describe

  protected def newConfig(overrides: Map[String, String]) = {
    import scala.collection.JavaConverters._
    ConfigFactory.parseMap(overrides.asJava)
  }

  def withFallback(fallback: Config): ServerConfig = new ServerConfig(config.withFallback(fallback))

  def withOverrides(overrides: Config): ServerConfig = new ServerConfig(overrides).withFallback(config)
}

object ServerConfig {
  def apply(config: Config = ConfigFactory.load()) = new ServerConfig(config)
}
