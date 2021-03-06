package agora.rest

import java.util.concurrent.atomic.AtomicInteger

import agora.api.data.Lazy
import agora.api.worker.HostLocation
import agora.config.{RichConfigOps, configForArgs}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future
import scala.util.Try

/**
  * A base parsed configuration based on an 'agora-defaults' configuration
  */
class ServerConfig(val config: Config) extends RichConfigOps with AutoCloseable {

  def actorSystemName: String = config.getString("actorSystemName")

  def host = config.getString("host")

  def hostResolver: HostResolver = {
    HostResolver(config.getString("resolvedHost"), location)
  }

  def port = config.getInt("port")

  def launchBrowser = config.getBoolean("launchBrowser")

  /**
    * Used by [[RunningService.start]]
    * @return true if we should accept user input from std-in to stop the service
    */
  def waitOnUserInput = config.getBoolean("waitOnUserInput")

  def includeSwaggerRoutes = config.getBoolean("includeSwaggerRoutes")

  def includeUIRoutes = config.getBoolean("includeUIRoutes")

  def staticPath = config.getString("staticPath")

  def defaultUIPath = config.getString("defaultUIPath")

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
  implicit def clientConfig: ClientConfig = lazyClientConfig.value
  private val lazyClientConfig = Lazy {
    val clientConf: Config = config.getConfig("client")
    val fixedPort          = Array(s"port=${port}").filter(_ => clientConf.getInt("port") <= 0)

    val fixedHost = Array(s"host=${host}").filter(_ => clientConf.getString("host").isEmpty)
    val sanitized = clientConf.withUserArgs(fixedHost ++ fixedPort)
    new ClientConfig(sanitized)
  }

  override def close() = stop()

  def stopClients(): Future[Any] = Future.fromTry(Try(lazyClientConfig.close()))

  def stopServer(): Future[Any] = Future.fromTry(Try(lazyServerImplicits.close()))

  def stop(): Future[Any] = {
    val stopFut1 = stopClients()
    val stopFut2 = stopServer()
    import scala.concurrent.ExecutionContext.Implicits._
    Future.sequence(List(stopFut1, stopFut2)).map(_ => Unit)
  }

  private[this] val uniqueActorNameCounter = new AtomicInteger(0)

  def nextActorSystemName(): String = uniqueActorNameCounter.getAndAdd(1) match {
    case 0 => actorSystemName
    case n => s"$actorSystemName-$n"
  }

  private val lazyServerImplicits = Lazy {
    newSystem(s"${actorSystemName}-server")
  }

  // this needs to be a lazy val instead of a def so we can 'import serverImplicits._'
  lazy val serverImplicits: AkkaImplicits = lazyServerImplicits.value

  def newSystem(name: String = nextActorSystemName): AkkaImplicits = AkkaImplicits(name, config)

  protected def newConfig(overrides: Map[String, String]) = {
    import scala.collection.JavaConverters._
    ConfigFactory.parseMap(overrides.asJava)
  }

  def withFallback(fallback: Config): ServerConfig = new ServerConfig(config.withFallback(fallback))

  def withOverrides(overrides: Config): ServerConfig = new ServerConfig(overrides).withFallback(config)

  override def toString = config.describe
}

object ServerConfig {
  def apply(firstArg: String, theRest: String*): ServerConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallbackConfig: Config = ConfigFactory.load("agora-defaults.conf")): ServerConfig = {
    val wc = apply(configForArgs(args, fallbackConfig))
    wc.withFallback(load().config)
  }

  def load() = apply(ConfigFactory.load())

  def apply(config: Config): ServerConfig = new ServerConfig(config)
}
