package finance.rest.server

import java.net.URI

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import finance.api.Ledger

import scala.concurrent.Future
import scala.io.StdIn

/**
  * Main entry point for the rest service.
  */
object RestService extends StrictLogging {

  def main(args: Array[String]) = {
    val conf: ServerConfig = {
      import finance.domain.RichConfig.implicits._
      val typesafeConfig = args.asConfig().withFallback(ServerConfig.defaultConfig)
      ServerConfig(typesafeConfig)
    }
    val future = start(conf)

    if (conf.launchBrowser && java.awt.Desktop.isDesktopSupported()) {
      import conf.implicits.executionContext
      future.onComplete { _ =>
        java.awt.Desktop.getDesktop().browse(new URI(s"http://localhost:${conf.port}/ui")) //index.html
      }
    }

    StdIn.readLine() // let it run until user presses return
    future.foreach(_.stop)(conf.implicits.executionContext)
  }

  /**
    * Represents a running services
    *
    * @param conf
    * @param binding
    */
  case class RunningService(conf: ServerConfig, binding: Http.ServerBinding) {
    def stop() = {
      binding.unbind() // trigger unbinding from the port
      //conf.implicits.system.terminate()
    }
  }


  def start(conf: ServerConfig = ServerConfig(), ledger: Ledger = Ledger()): Future[RunningService] = {
    import conf.implicits._
    val route: Route = FinanceRoutes(ledger).routes
    logger.info(s"Starting server at http://${conf.host}:${conf.port}")
    val bindingFuture = Http().bindAndHandle(route, conf.host, conf.port)
    bindingFuture.map { b =>
      RunningService(conf, b)
    }
  }


}