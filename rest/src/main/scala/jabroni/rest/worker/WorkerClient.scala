package jabroni.rest.worker

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.stream.Materializer
import io.circe.{Encoder, Json}
import jabroni.api.`match`.MatchDetails
import jabroni.api.worker.{HostLocation, WorkerDetails}
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
                        workerDetails: WorkerDetails)(implicit ec: ExecutionContext) {

  import WorkerClient._

  def sendRequest[T: ToEntityMarshaller](request: T)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    send(newRequest(request))
  }

  def sendMultipart(multipart: Multipart)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    send(newRequest(multipart))
  }

  def send(req: HttpRequest) = rest.send(req)

  def newRequest[T: ToEntityMarshaller](request: T): HttpRequest = dispatchRequest(path, matchDetails, request)

  def newRequest(multipart: Multipart)(implicit ec: ExecutionContext): HttpRequest = {
    multipartRequest(path, matchDetails, multipart)
  }
}

object WorkerClient {

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

  def apply(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer) = {
    import mat._
    val rest = RestClient(location)
    (path: String, matchDetails: MatchDetails, workerDetails: WorkerDetails) => new WorkerClient(rest, path, matchDetails, workerDetails)
  }

  def anEncoder[T](req: T)(implicit encoder: Encoder[T]): Marshaller[T, MessageEntity] = {
    val sm: Marshaller[Json, MessageEntity] = Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)((_: Json).noSpaces)
    sm.compose(encoder.apply)
  }

}
