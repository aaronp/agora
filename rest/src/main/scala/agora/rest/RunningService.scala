package agora.rest

import java.net.{InetSocketAddress, URI}

import agora.api.worker.HostLocation
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.io.StdIn

/**
  * Represents a running service - something which can be returning from starting a service that contains both
  * the binding and the config/service which was started
  */
case class RunningService[C <: ServerConfig, Service](conf: C, service: Service, binding: Http.ServerBinding) extends AutoCloseable {

  def location                        = HostLocation(localAddress.getHostName, localAddress.getPort).ensuring(_ == conf.location)
  def localAddress: InetSocketAddress = binding.localAddress
  private val shutdownPromise         = Promise[Unit]()
  private lazy val shutdown = {
    val future: Future[Unit] = binding.unbind()
    shutdownPromise.tryCompleteWith(future)
    future
  }

  def stop(): Future[Unit] = shutdown

  def onShutdown(thunk: => Unit)(implicit ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global) = {

    shutdownPromise.future.onComplete {
      case _ => thunk
    }
  }

  override def close(): Unit = stop()
}

object RunningService extends LazyLogging {

  def start[C <: ServerConfig, T](serverConfig: C, inputRoutes: Route, svc: T): Future[RunningService[C, T]] = {
    import serverConfig.host
    import serverConfig.port
    import serverConfig.launchBrowser
    import serverConfig.waitOnUserInput
    //    import serverConfig.actorSystemName
    import serverConfig.serverImplicits._

    logger.debug(s"Starting ${actorSystemName} at http://${host}:${port}")

    val routes = Route.seal(inputRoutes)
    val future: Future[RunningService[C, T]] = http.bindAndHandle(routes, host, port).map { b =>
      logger.info(s"Started ${serverConfig.actorSystemName} at http://${host}:${port}")
      RunningService[C, T](serverConfig, svc, b)
    }

    if (launchBrowser && java.awt.Desktop.isDesktopSupported()) {
      future.onComplete { _ =>
        java.awt.Desktop.getDesktop().browse(new URI(s"http://${host}:${port}/ui/index.html"))
      }
    }

    if (waitOnUserInput) {
      logger.info(s"Hit any key to stop ...")
      StdIn.readLine() // let it run until user presses return
      future.foreach(_.stop)
    }
    future
  }

}
