package jabroni.rest.worker

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model._
import akka.stream.Materializer
import io.circe.{Encoder, Json}
import jabroni.api.`match`.MatchDetails
import jabroni.api.worker.HostLocation
import jabroni.rest.MatchDetailsExtractor
import jabroni.rest.client.RestClient

import scala.concurrent.{ExecutionContext, Future}

/**
  * Can send requests to a worker
  *
  * @param rest
  */
case class WorkerClient(rest: RestClient) {

  import WorkerClient._

  def dispatch[T: ToEntityMarshaller](path: String, matchDetails: MatchDetails, request: T)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val req = dispatchRequest(path, matchDetails, request)
    rest.send(req)
  }

  def multipart(path: String, matchDetails: MatchDetails, multipart: Multipart)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val req = multipartRequest(path, matchDetails, multipart)
    rest.send(req)
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

  def apply(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer): WorkerClient = {
    val rest = RestClient(location)
    new WorkerClient(rest)
  }

  def anEncoder[T](req: T)(implicit encoder: Encoder[T]): Marshaller[T, MessageEntity] = {
    val sm: Marshaller[Json, MessageEntity] = Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)((_: Json).noSpaces)
    sm.compose(encoder.apply)
  }

}
