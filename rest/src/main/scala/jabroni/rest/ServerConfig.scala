package jabroni.rest

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
import jabroni.api.worker.HostLocation

import scala.concurrent.Future
import scala.io.StdIn

/**
  * A parsed configuration for our jabroni app
  */
trait ServerConfig extends LazyLogging {

  type Me <: ServerConfig

  def config: Config

  protected def self: Me

  def actorSystemName: String = "jabroni"

  object implicits {
    implicit lazy val system = ActorSystem(actorSystemName)
    implicit lazy val materializer = ActorMaterializer()
    implicit lazy val executionContext = system.dispatcher
  }

  def host = config.getString("host")
  def port = config.getInt("port")
  def launchBrowser = config.getBoolean("launchBrowser")
  def waitOnUserInput = config.getBoolean("waitOnUserInput")
  def runUser = config.getString("runUser")
  def includeUIRoutes = config.getBoolean("includeUIRoutes")

  def location = HostLocation(host, port)

  def runWithRoutes[T](name: String, routes: Route, svc: T): Future[RunningService[Me, T]] = {
    import implicits._

    logger.info(s"Starting $name at http://${host}:${port}")

    val future: Future[RunningService[Me, T]] = Http().bindAndHandle(routes, host, port).map { b =>
      RunningService(self, svc, b)
    }

    if (launchBrowser && java.awt.Desktop.isDesktopSupported()) {
      future.onComplete { _ =>
        java.awt.Desktop.getDesktop().browse(new URI(s"http://${host}:${port}/ui/index.html"))
      }
    }

    if (waitOnUserInput) {
      StdIn.readLine() // let it run until user presses return
      future.foreach(_.stop)
    }
    future
  }


}