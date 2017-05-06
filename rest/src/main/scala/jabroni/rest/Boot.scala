package jabroni.rest

import java.net.URI

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser.parse

import scala.concurrent.Future
import scala.io.StdIn
//
///**
//  * Main entry point for the rest service.
//  */
//trait Boot extends StrictLogging {
//
//  type Service
//
//  //  def main(args: Array[String]): Unit = start(args)
//
//  //  def start(args: Array[String] = Array.empty, defaultConfig: Config): Future[RunningService] = {
//  //    val conf = Boot.configForArgs(args, defaultConfig)
//  //
//  //    import conf.implicits.executionContext
//  //    val future = startFromConfig(conf)
//  //
//  //    if (conf.launchBrowser && java.awt.Desktop.isDesktopSupported()) {
//  //      future.onComplete { _ =>
//  //        java.awt.Desktop.getDesktop().browse(new URI(s"http://${conf.host}:${conf.port}/ui/index.html"))
//  //      }
//  //    }
//  //
//  //    if (conf.waitOnUserInput) {
//  //      StdIn.readLine() // let it run until user presses return
//  //      future.foreach(_.stop)(conf.implicits.executionContext)
//  //    }
//  //    future
//  //  }
//
//  //  def startFromConfig(conf: ServerConfig): Future[RunningService] = {
//  //    import conf.implicits._
//  //    val svc = serviceFromConf(conf)
//  //    val routeFuture = routeFromService(conf, svc)
//  //    routeFuture.flatMap(route => start(route, svc, conf))
//  //  }
//  //
//  //  protected def serviceFromConf(conf: ServerConfig): Service
//  //
//  //  protected def routeFromService(conf: ServerConfig, svc: Service): Future[Route]
//
//  //  def  //= ServerConfig.defaultConfig("jabroni.server")
//
//  //  def configForArgs(args: Array[String], defaultConfig: Config) = Boot.configForArgs(args, defaultConfig)
//
//  def start(route: Route, svc: Service, conf: ServerConfig): Future[RunningService] = {
//    import conf.implicits._
//    logger.info(s"Starting server at http://${conf.host}:${conf.port}")
//    val bindingFuture = Http().bindAndHandle(route, conf.host, conf.port)
//    val future = bindingFuture.map { b =>
//      RunningService(conf, svc, b)
//    }
//
//    if (conf.launchBrowser && java.awt.Desktop.isDesktopSupported()) {
//      future.onComplete { _ =>
//        java.awt.Desktop.getDesktop().browse(new URI(s"http://${conf.host}:${conf.port}/ui/index.html"))
//      }
//    }
//
//    if (conf.waitOnUserInput) {
//      StdIn.readLine() // let it run until user presses return
//      future.foreach(_.stop)(conf.implicits.executionContext)
//    }
//    future
//  }
//
//  /**
//    * Represents a running services
//    *
//    * @param conf
//    * @param binding
//    */
//  case class RunningService(conf: ServerConfig, service: Service, binding: Http.ServerBinding) {
//    def stop() = binding.unbind()
//  }
//
//}

object Boot {

  def configForArgs(args: Array[String], defaultConfig: Config) = {
    import jabroni.domain.RichConfig.implicits._
    args.asConfig().withFallback(defaultConfig).resolve()
  }


  def asJson(c: Config) = {
    val json = c.root.render(ConfigRenderOptions.concise().setJson(true))
    parse(json).right.get
  }
}