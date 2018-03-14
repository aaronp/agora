package agora.rest.client

import java.util.concurrent.atomic.AtomicInteger

import agora.io.AlphaCounter
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import com.typesafe.scalalogging.StrictLogging

import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Properties, Success}

/**
  * A client which adds an X-Request-Id header on each request in the X-Request-Id header
  *
  * @param underlying         the underlying client which will send requests
  * @param uniqueClientPrefix an id prefix
  * @param firstId            the unique ID seed
  */
class UniqueRequestIdRestClient(underlying: RestClient, uniqueClientPrefix: String, firstId: Long, requestSLAThreshold: FiniteDuration = 250.millis)
    extends RestClientDelegate(underlying)
    with StrictLogging {
  private val idCounter = AlphaCounter.from(firstId)

  protected def nextUniqueIdHeader() = s"${uniqueClientPrefix}${idCounter.next()}"

  private val hostPort = UniqueRequestIdRestClient.hostPortForClient(underlying)

  override def send(inRequest: HttpRequest): Future[HttpResponse] = {
    val started = Platform.currentTime

    val reqId         = nextUniqueIdHeader
    val h: HttpHeader = header.`X-Request-Id`(reqId)
    val request       = inRequest.copy(headers = h +: inRequest.headers)
    val future        = super.send(request)

    def took = Platform.currentTime - started

    future.onComplete {
      case Success(resp) =>
        if (resp.status.intValue() != 200 || took > requestSLAThreshold.toMillis) {
          logger.warn(s"${request.method.name()} ${hostPort}${request.uri} ($reqId) took ${took}ms (status ${resp.status})")
        } else {
          logger.debug(s"${request.method.name()} ${hostPort}${request.uri} ($reqId) took ${took}ms")
        }
      case Failure(err) =>
        logger.error(s"${request.method.name()} ${hostPort}${request.uri} ($reqId) took ${took} and threw ${err}")
    }

    future

  }
}

object UniqueRequestIdRestClient {
  def hostPortForClient(rc: RestClient): String = {
    rc match {
      case actor: AkkaClient  => actor.location.asURL
      case retry: RetryClient => hostPortForClient(retry.client)
      case _                  => "?"
    }
  }

  def host = agora.config.propOrEnv("HOST").getOrElse("NO_HOST")

  def user = Properties.userName

  def defaultPrefix() = s"$host-${user}-"

  private val startTime = Platform.currentTime
  private val instances = new AtomicInteger(0)

  /**
    * Come up w/ an index prefix
    *
    * @param fingerInTheAirGuessAtHowManyRequestsASingleClientWillSend
    * @return
    */
  private def nextStartId(fingerInTheAirGuessAtHowManyRequestsASingleClientWillSend: Int = 1000000): Long = {
    (Platform.currentTime - startTime) + (instances.getAndIncrement() * fingerInTheAirGuessAtHowManyRequestsASingleClientWillSend)
  }

  /**
    *
    * @param underlying         the underlying client which will have http requests passed through to it w/ a unique header appender
    * @param uniqueClientPrefix some helpful prefix used as a prefix to all requests sent w/ this client
    * @param firstId            the first value as a seed for the unique id counter
    * @return a RestClient which will send unique request IDs as a X-Request-Id header
    */
  def apply(underlying: RestClient, uniqueClientPrefix: String = defaultPrefix(), firstId: Long = nextStartId()) = {
    new UniqueRequestIdRestClient(underlying, uniqueClientPrefix, firstId)
  }
}
