package jabroni.rest.client

import java.time.LocalDateTime

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import RetryClient._
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NonFatal

/**
  * A client which will try multiple clients
  *
  * @param mkClient
  */
class RetryClient(mkClient: () => RestClient, onError: RetryStrategy) extends RestClient with StrictLogging {

  private var clientOpt: Option[RestClient] = None
  private var crashHistory = new Crashes(Nil)

  override def toString = {
    s"RetryClient($onError, client: ${clientOpt})"
  }

  private object Lock

  /**
    * Resets the client. This may be invoked externally in case of e.g. server 503 errors et al
    */
  def reset() = close()

  def client: RestClient = Lock.synchronized {
    clientOpt.getOrElse {
      val c = mkClient()
      require(c.isInstanceOf[RetryClient] == false, "nested retrying clients found")
      logger.debug(s"Creating a new underlying client $c")
      clientOpt = Option(c)
      c
    }
  }

  private def handle(request: HttpRequest, e: Throwable): Future[HttpResponse] = {
    crashHistory = crashHistory.add(Crash(e))
    onError(crashHistory)

    // if we get this far, our strategy hasn't propagated the exception
    reset()
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

  override def close(): Unit = {
    Lock.synchronized {
      logger.debug(s"Closing $clientOpt")
      Try(clientOpt.foreach(_.close))
      clientOpt = None
    }
  }

  override implicit def materializer: Materializer = client.materializer
}

object RetryClient {

  def apply(strategy: RetryStrategy)(newClient: () => RestClient) = new RetryClient(newClient, strategy)

  /**
    * Exposes a DSL for creating an exception strategy
    *
    * @param n
    */
  case class tolerate(n: Int) {
    def failuresWithin(d: FiniteDuration) = new CountingStrategy(n, d)
  }


  case class Crash(error: Throwable, time: LocalDateTime = LocalDateTime.now)

  implicit object LocalDateTimeOrdering extends Ordering[LocalDateTime] {
    override def compare(x: LocalDateTime, y: LocalDateTime): Int = x.compareTo(y)
  }

  case class Crashes(list: List[Crash]) {
    override def toString = exception.toString

    def size = list.size

    def add(crash: Crash) = copy(crash :: list)

    def removeBefore(threshold: LocalDateTime = LocalDateTime.now) = {
      copy(list = list.filterNot(_.time.isBefore(threshold)))
    }

    lazy val exception = list match {
      case Nil => new Exception("Empty Crash!")
      case head :: tail =>
        tail.map(_.error).foreach(head.error.addSuppressed)
        head.error
    }
  }

  // given some crash info, either prune that crash info or throw an exception
  type RetryStrategy = Crashes => Crashes

  class CountingStrategy(nTimes: Int, in: FiniteDuration) extends RetryStrategy {
    override def toString = s"RetryStrategy($nTimes in $in)"

    private def logger = LoggerFactory.getLogger(getClass)

    def filter(crashes: Crashes, now: LocalDateTime): Crashes = {
      val threshold = now.minusNanos(in.toNanos)
      val filtered = crashes.removeBefore(threshold)
      if (filtered.size > nTimes) {
        logger.error(s"Propagating ${crashes.exception}")
        throw crashes.exception
      } else {
        filtered
      }
    }

    override def apply(crashes: Crashes): Crashes = filter(crashes, LocalDateTime.now)
  }

}