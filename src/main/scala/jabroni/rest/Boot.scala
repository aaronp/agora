package jabroni.rest

import java.net.URI

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.io.StdIn

/**
  * Main entry point for the rest service.
  */
trait Boot extends StrictLogging {

  def routeFromConf(conf: ServerConfig): Route

  def main(args: Array[String]) = {
    val conf: ServerConfig = {
      import jabroni.domain.RichConfig.implicits._
      val typesafeConfig = args.asConfig().withFallback(ServerConfig.defaultConfig)
      ServerConfig(typesafeConfig)
    }

    import conf.implicits.executionContext
    val route = routeFromConf(conf)
    val future = start(route, conf)

    if (conf.launchBrowser && java.awt.Desktop.isDesktopSupported()) {
      future.onComplete { _ =>
        java.awt.Desktop.getDesktop().browse(new URI(s"http://${conf.host}:${conf.port}/ui")) //index.html
      }
    }

    if (conf.waitOnUserInput) {
      StdIn.readLine() // let it run until user presses return
      future.foreach(_.stop)(conf.implicits.executionContext)
    }
  }

  /**
    * Represents a running services
    *
    * @param conf
    * @param binding
    */
  case class RunningService(conf: ServerConfig, binding: Http.ServerBinding) {
    def stop() = binding.unbind()
  }


  def start(route: Route, conf: ServerConfig = ServerConfig()): Future[RunningService] = {
    import conf.implicits._
    logger.info(s"Starting server at http://${conf.host}:${conf.port}")
    val bindingFuture = Http().bindAndHandle(route, conf.host, conf.port)
    bindingFuture.map { b =>
      RunningService(conf, b)
    }
  }


}
