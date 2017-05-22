package jabroni.rest.worker

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Encoder, Json}
import jabroni.api.`match`.MatchDetails
import jabroni.api.worker.{HostLocation, WorkerDetails}
import jabroni.health.HealthDto
import jabroni.rest.MatchDetailsExtractor
import jabroni.rest.client.RestClient

import scala.concurrent.{ExecutionContext, Future}

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
                        workerDetails: WorkerDetails)(implicit mat: Materializer) {

  import WorkerClient._
  import mat.executionContext

  def sendRequest[T: ToEntityMarshaller](request: T): Future[HttpResponse] = {
    send(newRequest(request))
  }

  def health = WorkerClient.health(rest)

  def sendMultipart(multipart: Multipart): Future[HttpResponse] = {
    send(newRequest(multipart))
  }

  def send(req: HttpRequest) = rest.send(req)

  def newRequest[T: ToEntityMarshaller](request: T): HttpRequest = dispatchRequest(path, matchDetails, request)

  def newRequest(multipart: Multipart): HttpRequest = {
    multipartRequest(path, matchDetails, multipart)
  }
}

object WorkerClient extends FailFastCirceSupport with LazyLogging {


  def health(restClient: RestClient)(implicit mat: Materializer): Future[HealthDto] = {
    import mat._
    restClient.send(WorkerHttp.healthRequest).flatMap { resp =>
//      import io.circe.generic.auto._
//      val x: FromEntityUnmarshaller[HealthDto] = unmarshaller[HealthDto]
      Unmarshal(resp).to[HealthDto]
    }
  }


  def multipartRequest(path: String, matchDetails: MatchDetails, multipart: Multipart)(implicit ec: ExecutionContext): HttpRequest = {
    val httpRequest: HttpRequest = WorkerHttp("multipart", path, multipart)
    val headers = MatchDetailsExtractor.headersFor(matchDetails)
    httpRequest.withHeaders(headers ++ httpRequest.headers)
  }

  def dispatchRequest[T: ToEntityMarshaller](path: String, matchDetails: MatchDetails, request: T)(implicit ec: ExecutionContext): HttpRequest = {
    val httpRequest: HttpRequest = WorkerHttp("worker", path, request)
    val headers = MatchDetailsExtractor.headersFor(matchDetails)
    httpRequest.withHeaders(headers)
  }

  def apply(rest: RestClient)(implicit sys: ActorSystem, mat: Materializer): (String, MatchDetails, WorkerDetails) => WorkerClient = {
    (path: String, matchDetails: MatchDetails, workerDetails: WorkerDetails) => new WorkerClient(rest, path, matchDetails, workerDetails)
  }

  def apply(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer): (String, MatchDetails, WorkerDetails) => WorkerClient = {
    val rest = RestClient(location)
    apply(rest)
  }

  def anEncoder[T](req: T)(implicit encoder: Encoder[T]): Marshaller[T, MessageEntity] = {
    val sm: Marshaller[Json, MessageEntity] = Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)((_: Json).noSpaces)
    sm.compose(encoder.apply)
  }

}
