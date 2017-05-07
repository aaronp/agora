package jabroni.rest.worker

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.HttpRequest

import scala.concurrent.ExecutionContext

object WorkerHttp extends RequestBuilding {

  def apply[T: ToEntityMarshaller](path: String, request: T)(implicit ec: ExecutionContext): HttpRequest = {
    Post(s"/rest/worker/$path", request)
  }
}
