package agora.rest

import java.net.{InetSocketAddress, URI}

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.io.StdIn

/**
  * Represents a running service - something which can be returning from starting a service that contains both
  * the binding and the config/service which was started
  */
case class RunningService[C <: ServerConfig, Service](conf: C, service: Service, binding: Http.ServerBinding) extends AutoCloseable with LazyLogging {

  def localAddress: InetSocketAddress = binding.localAddress

  private val shutdownPromise = Promise[Unit]()
  private lazy val shutdown = {
    logger.info(s"Unbinding RunningService '${conf.actorSystemName}-server' on ${conf.location} (running on ${localAddress})")
    import ExecutionContext.Implicits.global

    // start shutting down local clients first so they don't keep trying to reconnect.
    // this is really just for a single-host test environment and doesn't really matter in production

    val future: Future[Unit] = {
      service match {
        case auto: AutoCloseable => auto.close()
        case _                   =>
      }
      val unbindFut = binding.unbind()
      val confFut   = conf.stop()
      Future.sequence(List(unbindFut, confFut)).map(_ => Unit)
    }

    shutdownPromise.tryCompleteWith(future)
    shutdownPromise.future
  }

  def stop(): Future[Unit] = shutdown

  def onShutdown(thunk: => Unit)(implicit ec: ExecutionContext = ExecutionContext.Implicits.global) = {
    shutdownPromise.future.onComplete {
      case _ => thunk
    }
  }

  override def close(): Unit = stop()
}

object RunningService extends LazyLogging {

  def start[C <: ServerConfig, T](serverConfig: C, inputRoutes: Route, svc: T): Future[RunningService[C, T]] = {
    import serverConfig.serverImplicits._
    import serverConfig.{host, launchBrowser, location, port, waitOnUserInput}

    logger.debug(s"Starting ${actorSystemName} at ${location.asHostPort}")

    val routes = Route.seal(inputRoutes)
    val future: Future[RunningService[C, T]] = http.bindAndHandle(routes, host, port).map { b =>
      logger.info(s"Started ${serverConfig.actorSystemName} at ${serverConfig.location.asHostPort}")
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
