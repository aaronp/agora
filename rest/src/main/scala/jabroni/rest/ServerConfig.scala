package jabroni.rest

import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import jabroni.api.worker.HostLocation
import jabroni.domain.RichConfigOps
import jabroni.rest.client.RestClient

import scala.concurrent.Future

/**
  * A parsed configuration for our jabroni app
  */
class ServerConfig(val config: Config) extends RichConfigOps {

  def actorSystemName: String = config.getString("actorSystemName")

  lazy val implicits = new AkkaImplicits(actorSystemName)

  def host = config.getString("host")
  def port = config.getInt("port")
  def launchBrowser = config.getBoolean("launchBrowser")
  def waitOnUserInput = config.getBoolean("waitOnUserInput")
  def runUser = config.getString("runUser")
  def includeUIRoutes = config.getBoolean("includeUIRoutes")
  def enableSupportRoutes = config.getBoolean("enableSupportRoutes")
  def chunkSize = config.getInt("chunkSize")

  def location = HostLocation(host, port)

  lazy val restClient: RestClient = {
    import implicits._
    RestClient(location)
  }

  def runWithRoutes[T](routes: Route, svc: T): Future[RunningService[ServerConfig, T]] = {
    RunningService.start(this, routes, svc)
  }

  override def toString = config.describe
}

object ServerConfig {
  def apply(config : Config) = new ServerConfig(config)
  def unapply(config : ServerConfig) = Option(config.config)
}