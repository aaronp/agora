package agora.rest

import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import agora.api.worker.HostLocation
import agora.domain.{RichConfig, RichConfigOps}
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

  def includeUIRoutes = config.getBoolean("includeUIRoutes")

  def enableSupportRoutes = config.getBoolean("enableSupportRoutes")

  def chunkSize = config.getInt("chunkSize")

  def location = HostLocation(host, port)

  lazy val clientConfig = {
    val clientConf: Config = config.getConfig("client")
    val sanitized = if (clientConf.getInt("port") <= 0) {
      clientConf.withUserArgs(Array(s"port=${port}"))
    } else {
      clientConf
    }
    new ClientConfig(sanitized)
  }

  def hostLocationForConfig(c: Config) = {
    HostLocation(c.getString("host"), c.getInt("port"))
  }

  private[this] val uniqueActorNameCounter = new AtomicInteger(0)

  def nextActorSystemName() = uniqueActorNameCounter.getAndAdd(1) match {
    case 0 => actorSystemName
    case n => s"$actorSystemName-$n"
  }

  lazy val serverImplicits = newSystem(s"${actorSystemName}-server")

  def newSystem(name: String = nextActorSystemName): AkkaImplicits = new AkkaImplicits(name, config)

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
