package jabroni.rest.worker

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.HttpRequest

import scala.concurrent.ExecutionContext

object WorkerHttp extends RequestBuilding {

  def apply[T: ToEntityMarshaller](prefix : String, path: String, request: T)(implicit ec: ExecutionContext): HttpRequest = {
    Post(s"/rest/$prefix/$path", request)
  }
}
