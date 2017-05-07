package jabroni.rest.worker

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshalling.{Opaque, WithFixedContentType, WithOpenCharset}
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller, ToRequestMarshaller}
import akka.http.scaladsl.model._
import akka.stream.Materializer
import io.circe.{DecodingFailure, Encoder, Json}
import jabroni.api.{JobId, MatchId}
import jabroni.api.`match`.MatchDetails
import jabroni.api.worker.{HostLocation, WorkerDetails}
import jabroni.rest.MatchDetailsExtractor
import jabroni.rest.client.RestClient

import scala.concurrent.{ExecutionContext, Future}

/**
  * Can send requests to a worker
  *
  * @param rest
  */
case class WorkerClient(rest: RestClient) {

  def dispatch[T: ToEntityMarshaller](path: String, matchDetails: MatchDetails, request: T)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val httpRequest: HttpRequest = WorkerHttp(path, request)
    val headers = MatchDetailsExtractor.headersFor(matchDetails)
    rest.send(httpRequest.withHeaders(headers))

  }
}

object WorkerClient {
  def apply(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer): WorkerClient = {
    val rest = RestClient(location)
    new WorkerClient(rest)
  }

  def anEncoder[T](req: T)(implicit encoder: Encoder[T]): Marshaller[T, MessageEntity] = {
    val sm: Marshaller[Json, MessageEntity] = Marshaller.StringMarshaller.wrap(MediaTypes.`application/json`)((_: Json).noSpaces)
    sm.compose(encoder.apply)
  }

}
