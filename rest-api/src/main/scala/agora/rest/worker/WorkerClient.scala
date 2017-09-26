package agora.rest.worker

import agora.api.`match`.MatchDetails
import agora.api.exchange.{Dispatch, AsClient}
import agora.api.health.HealthDto
import agora.api.worker.WorkerDetails
import agora.rest.client.RestClient
import agora.rest.multipart.MultipartBuilder
import agora.rest.{ClientConfig, MatchDetailsExtractor}
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Encoder, Json}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
  * A short-lived handle to something which can send requests to a worker
  *
  * It also contains a context in which the request are sent (e.g. the match details, worker details and path)
  *
  * @param rest
  */
case class WorkerClient(rest: RestClient, matchDetails: MatchDetails, workerDetails: WorkerDetails) {

  import WorkerClient._

  def path = workerDetails.path

  protected implicit def mat = rest.materializer

  protected implicit def ec = rest.executionContext

  def health = WorkerClient.health(rest)

  def sendRequest[T: ToEntityMarshaller](request: T): Future[HttpResponse] = {
    sendDirect(newRequest(request))
  }

  def send(req: HttpRequest): Future[HttpResponse] =
    sendDirect(req.withHeaders(MatchDetailsExtractor.headersFor(matchDetails)))

  private def sendDirect(req: HttpRequest): Future[HttpResponse] = rest.send(req)

  def send(reqBuilder: MultipartBuilder)(implicit timeout: FiniteDuration): Future[HttpResponse] = {
    reqBuilder.formData.flatMap { (strict: Multipart.FormData.Strict) =>
      sendDirect(newRequest(strict))
    }
  }

  def newRequest[T: ToEntityMarshaller](request: T): HttpRequest = dispatchRequest(path, matchDetails, request)
}

object WorkerClient extends FailFastCirceSupport with LazyLogging {

  def apply(conf: ClientConfig, dispatch: Dispatch[_]): WorkerClient = {
    val rest = conf.clientFor(dispatch.location)
    new WorkerClient(rest, dispatch.matchDetails, dispatch.matchedWorker)
  }

  def health(restClient: RestClient): Future[HealthDto] = {
    import restClient._
    restClient.send(WorkerHttp.healthRequest).flatMap { resp =>
      Unmarshal(resp).to[HealthDto]
    }
  }

  def multipartRequest(path: String, matchDetails: MatchDetails, multipart: Multipart)(
      implicit ec: ExecutionContext): HttpRequest = {
    val httpRequest: HttpRequest = WorkerHttp(path, multipart)
    val headers                  = MatchDetailsExtractor.headersFor(matchDetails)
    httpRequest.withHeaders(headers ++ httpRequest.headers)
  }

  def dispatchRequest[T: ToEntityMarshaller](path: String, matchDetails: MatchDetails, request: T)(
      implicit ec: ExecutionContext): HttpRequest = {
    val httpRequest: HttpRequest = WorkerHttp(path, request)
    val headers                  = MatchDetailsExtractor.headersFor(matchDetails)
    httpRequest.withHeaders(headers)
  }

  def anEncoder[T](req: T)(implicit encoder: Encoder[T]): Marshaller[T, MessageEntity] = {
    val sm: Marshaller[Json, MessageEntity] =
      Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)((_: Json).noSpaces)
    sm.compose(encoder.apply)
  }

}
