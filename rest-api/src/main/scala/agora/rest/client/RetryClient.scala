package agora.rest.client

import java.util.concurrent.locks.ReentrantLock

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * A client which will try multiple clients
  *
  * @param mkClient
  */
class RetryClient(mkClient: () => RestClient, onError: RetryStrategy) extends RestClient with StrictLogging {

  private var clientOpt: Option[RestClient] = None
  private var crashHistory                  = new Crashes(Nil)

  override def toString = {
    s"RetryClient(current=${clientOpt}, strategy=$onError)"
  }

  private val clientLock = new ReentrantLock(true)

  /**
    * Resets the client. This may be invoked externally in case of e.g. server 503 errors et al
    */
  def reset(err: Option[Throwable]): RetryClient = {
    try {
      err.foreach { e =>
        crashHistory = onError(crashHistory.add(Crashes.Crash(e)))
      }
    } finally {
      close()
    }

    // if we get this far, our strategy hasn't propagated the exception
    this
  }

  def client: RestClient = {
    clientLock.lock()
    try {
      clientOpt.getOrElse {
        val c = mkClient()
        require(c.isInstanceOf[RetryClient] == false, "nested retrying clients found")
        logger.debug(s"Creating a new underlying client $c")
        clientOpt = Option(c)
        c
      }
    } finally {
      clientLock.unlock()
    }
  }

  private def handle(request: HttpRequest, e: Throwable): Future[HttpResponse] = {
    reset(Option(e))
    send(request)
  }

  override def send(request: HttpRequest): Future[HttpResponse] = {
    val future = try {
      client.send(request)
    } catch {
      case NonFatal(e) => handle(request, e)
    }
    future.recoverWith {
      case err => handle(request, err)
    }
  }

  override implicit def materializer: Materializer = client.materializer

  override def stop(): Future[Any] = {
    clientLock.lock()
    val previousOpt = try {
      logger.debug(s"Closing $clientOpt")
      val res = clientOpt
      clientOpt = None
      res
    } finally {
      clientLock.unlock()
    }

    previousOpt.map(_.stop()) match {
      case Some(future) => future
      case None         => Future.successful(true)
    }
  }

}

object RetryClient {
  def apply(strategy: RetryStrategy)(newClient: () => RestClient) = new RetryClient(newClient, strategy)
}
