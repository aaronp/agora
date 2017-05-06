package jabroni.rest

import java.net.URI

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import jabroni.api.worker.HostLocation

import scala.concurrent.Future
import scala.io.StdIn

/**
  * A parsed configuration for our jabroni app
  */
trait ServerConfig extends BaseConfig with StrictLogging {

  type Me <: ServerConfig

  protected def self : Me
  //
//  type Service
//
//  protected def routes: Route
//
//  protected def newService: Service

  //  val default = ConfigFactory.load("")

  val host = config.getString("host")
  val port = config.getInt("port")
  val launchBrowser = config.getBoolean("launchBrowser")
  val waitOnUserInput = config.getBoolean("waitOnUserInput")
  val runUser = config.getString("runUser")
  val includeUIRoutes = config.getBoolean("includeUIRoutes")

  def location = HostLocation(host, port)

  def runWithRoutes[T](routes: Route, svc : T): Future[RunningService[Me, T]] = {
    import implicits._
    logger.info(s"Starting server at http://${host}:${port}")
    val bindingFuture = Http().bindAndHandle(routes, host, port)
    val future: Future[RunningService[Me, T]] = bindingFuture.map { b =>
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

//
//object ServerConfig {
////  def defaultConfig(path : String) = ConfigFactory.load().getConfig(path)
//
//  def apply(conf: Config): ServerConfig = new ServerConfig(conf)
//}
