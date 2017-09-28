package agora.rest

import java.util.concurrent.atomic.AtomicInteger

import agora.api.worker.HostLocation
import agora.config.RichConfigOps
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future

/**
  * A base parsed configuration based on an 'agora-defaults' configuration
  */
class ServerConfig(val config: Config) extends RichConfigOps {

  def actorSystemName: String = config.getString("actorSystemName")

  def host = config.getString("host")

  def hostResolver: HostResolver = {
    HostResolver(config.getString("resolvedHost"), location)
  }

  def port = config.getInt("port")

  def launchBrowser = config.getBoolean("launchBrowser")

  def waitOnUserInput = config.getBoolean("waitOnUserInput")

  def defaultUIPath = config.getString("defaultUIPath")

  def includeSwaggerRoutes = config.getBoolean("includeSwaggerRoutes")

  def enableSupportRoutes = config.getBoolean("enableSupportRoutes")

  def chunkSize = config.getInt("chunkSize")

  def location = HostLocation(host, port)

  /**
    * We have server settings like this:
    * {{{
    *   host : abc
    *   port : 123
    *
    *   client : {
    *     host : abc
    *     port : 123
    *   }
    * }}}
    * To allow the user to override run-time client and server settings in light of the two-step typesafe config
    * resolution, we allow the client to take on 'default' host and port values (empty string and 0, respectively)
    * so that we can fall-back to (resolved) server host/port values.
    */
  implicit lazy val clientConfig: ClientConfig = {
    val clientConf: Config = config.getConfig("client")
    val fixedPort          = Array(s"port=${port}").filter(_ => clientConf.getInt("port") <= 0)

    val fixedHost = Array(s"host=${host}").filter(_ => clientConf.getString("host").isEmpty)
    val sanitized = clientConf.withUserArgs(fixedHost ++ fixedPort)
    new ClientConfig(sanitized)
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
