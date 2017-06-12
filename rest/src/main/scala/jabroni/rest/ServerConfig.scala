package jabroni.rest

import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import jabroni.api.worker.HostLocation
import jabroni.domain.RichConfigOps
import jabroni.rest.client.{RestClient, RetryClient}

import concurrent.Future
import concurrent.duration._

/**
  * A parsed configuration for our jabroni app
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

  object clientFailover {
    val nTimes = config.getInt("clientFailover.nTimes")
    val within = config.getDuration("clientFailover.within").toMillis.millis

    def strategy = RetryClient.tolerate(nTimes).failuresWithin(within)
  }

  private val nameCounter = new AtomicInteger(0)

  def nextActorSystemName() = nameCounter.getAndAdd(1) match {
    case 0 => actorSystemName
    case n => s"$actorSystemName-$n"
  }

  lazy val serverImplicits = newSystem(s"${actorSystemName}-server")

  def newSystem(name : String = nextActorSystemName) = new AkkaImplicits(name)

  lazy val restClient: RestClient = retryClient(location)

  def retryClient(loc: HostLocation = location): RetryClient = RetryClient(clientFailover.strategy)(() => newRestClient(loc))

  def newRestClient(loc: HostLocation): RestClient = {
    RestClient(loc, () => (newSystem()).materializer)
  }

  def runWithRoutes[T](routes: Route, svc: T): Future[RunningService[ServerConfig, T]] = {
    RunningService.start(this, routes, svc)
  }

  override def toString = config.describe
}

object ServerConfig {
  def apply(config: Config) = new ServerConfig(config)

  def unapply(config: ServerConfig) = Option(config.config)
}