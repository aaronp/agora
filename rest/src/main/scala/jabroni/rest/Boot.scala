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

  type Service

  def main(args: Array[String]) = {
    val conf: ServerConfig = configForArgs(args)

    import conf.implicits.executionContext
    val future = start(conf)

    if (conf.launchBrowser && java.awt.Desktop.isDesktopSupported()) {
      future.onComplete { _ =>
        java.awt.Desktop.getDesktop().browse(new URI(s"http://${conf.host}:${conf.port}/ui/index.html"))
      }
    }

    if (conf.waitOnUserInput) {
      StdIn.readLine() // let it run until user presses return
      future.foreach(_.stop)(conf.implicits.executionContext)
    }
  }

  def start(conf: ServerConfig): Future[RunningService] = {
    import conf.implicits._
    val svc = serviceFromConf(conf)
    val routeFuture = routeFromService(conf, svc)
    routeFuture.flatMap(route => start(route, svc, conf))
  }
  protected def serviceFromConf(conf: ServerConfig): Service
  protected def routeFromService(conf: ServerConfig, svc: Service): Future[Route]

  def defaultConfig = ServerConfig.defaultConfig("jabroni.server")

  def configForArgs(args: Array[String]): ServerConfig = {
    import jabroni.domain.RichConfig.implicits._
    val typesafeConfig = args.asConfig().withFallback(defaultConfig)
    ServerConfig(typesafeConfig)
  }


  def start(route: Route, svc : Service, conf: ServerConfig): Future[RunningService] = {
    import conf.implicits._
    logger.info(s"Starting server at http://${conf.host}:${conf.port}")
    val bindingFuture = Http().bindAndHandle(route, conf.host, conf.port)
    bindingFuture.map { b =>
      RunningService(conf, svc, b)
    }
  }

  /**
    * Represents a running services
    *
    * @param conf
    * @param binding
    */
  case class RunningService(conf: ServerConfig, service : Service, binding: Http.ServerBinding) {
    def stop() = binding.unbind()
  }

}
