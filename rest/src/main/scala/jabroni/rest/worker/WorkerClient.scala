package jabroni.rest.worker

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToRequestMarshaller}
import akka.http.scaladsl.model.{HttpCharsets, HttpRequest, HttpResponse}
import akka.stream.Materializer
import io.circe.DecodingFailure
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

  def dispatch[T: ToRequestMarshaller](path: String, matchDetails: MatchDetails, request: T)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val m = implicitly[ToRequestMarshaller[T]]

    import Marshalling._
    m(request).flatMap { (marshallings: List[Marshalling[HttpRequest]]) =>
      val httpRequestOpt: Option[HttpRequest] = marshallings.collectFirst {
        case fixed: WithOpenCharset[HttpRequest] => fixed.marshal(HttpCharsets.`UTF-8`)
        case fixed: WithFixedContentType[HttpRequest] => fixed.marshal()
        case fixed: Opaque[HttpRequest] => fixed.marshal()
      }
      httpRequestOpt match {
        case None => Future.failed(new Exception(s"No marshalling found for $request"))
        case Some(initialRequest) =>
          val headers = MatchDetailsExtractor.headersFor(matchDetails)
          rest.send(initialRequest.withHeaders(headers))
      }
    }
  }
}

object WorkerClient {
  def apply(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer): WorkerClient = {
    val rest = RestClient(location)
    new WorkerClient(rest)
  }

}
