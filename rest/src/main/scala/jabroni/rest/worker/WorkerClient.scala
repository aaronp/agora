package jabroni.rest.worker

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Encoder, Json}
import jabroni.api.`match`.MatchDetails
import jabroni.api.worker.WorkerDetails
import jabroni.health.HealthDto
import jabroni.rest.MatchDetailsExtractor
import jabroni.rest.client.RestClient
import jabroni.rest.multipart.MultipartBuilder

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

/**
  * A short-lived handle to something which can send requests to a worker
  *
  * It also contains a context in which the request are sent (e.g. the match details, worker details and path)
  *
  * @param rest
  */
case class WorkerClient(rest: RestClient,
                        path: String,
                        matchDetails: MatchDetails,
                        workerDetails: WorkerDetails) {

  import WorkerClient._

  protected implicit def mat = rest.materializer

  protected implicit def ec = rest.executionContext

  def sendRequest[T: ToEntityMarshaller](request: T): Future[HttpResponse] = {
    send(newRequest(request))
  }

  def health = WorkerClient.health(rest)

  def sendMultipart(multipart: Multipart): Future[HttpResponse] = {
    send(newMultipartRequest(multipart))
  }

  def send(req: HttpRequest): Future[HttpResponse] = rest.send(req)

  def send(reqBuilder: MultipartBuilder)(implicit timeout: FiniteDuration): Future[HttpResponse] = {
    reqBuilder.formData.flatMap { (strict: Multipart.FormData.Strict) =>
      send(newMultipartRequest(strict))
    }
  }

  def newRequest[T: ToEntityMarshaller](request: T): HttpRequest = dispatchRequest(path, matchDetails, request)

  def newMultipartRequest(multipart: Multipart): HttpRequest = multipartRequest(path, matchDetails, multipart)
}

object WorkerClient extends FailFastCirceSupport with LazyLogging {


  def health(restClient: RestClient): Future[HealthDto] = {
    import restClient._
    restClient.send(WorkerHttp.healthRequest).flatMap { resp =>
      Unmarshal(resp).to[HealthDto]
    }
  }


  def multipartRequest(path: String, matchDetails: MatchDetails, multipart: Multipart)(implicit ec : ExecutionContext): HttpRequest = {
    val httpRequest: HttpRequest = WorkerHttp("multipart", path, multipart)
    val headers = MatchDetailsExtractor.headersFor(matchDetails)
    httpRequest.withHeaders(headers ++ httpRequest.headers)
  }

  def dispatchRequest[T: ToEntityMarshaller](path: String, matchDetails: MatchDetails, request: T)(implicit ec : ExecutionContext): HttpRequest = {
    val httpRequest: HttpRequest = WorkerHttp("worker", path, request)
    val headers = MatchDetailsExtractor.headersFor(matchDetails)
    httpRequest.withHeaders(headers)
  }

  def apply(rest: RestClient): (String, MatchDetails, WorkerDetails) => WorkerClient = {
    (path: String, matchDetails: MatchDetails, workerDetails: WorkerDetails) => new WorkerClient(rest, path, matchDetails, workerDetails)
  }

  def anEncoder[T](req: T)(implicit encoder: Encoder[T]): Marshaller[T, MessageEntity] = {
    val sm: Marshaller[Json, MessageEntity] = Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)((_: Json).noSpaces)
    sm.compose(encoder.apply)
  }

}
