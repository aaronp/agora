package agora.rest.worker

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.HttpRequest
import agora.rest.CommonRequestBuilding

import scala.concurrent.ExecutionContext

object WorkerHttp extends CommonRequestBuilding {

  def apply[T: ToEntityMarshaller](prefix: String, path: String, request: T)(implicit ec: ExecutionContext): HttpRequest = {
    Post(s"/rest/$prefix/$path", request).withCommonHeaders
  }

  val healthRequest: HttpRequest = Get("/rest/health")

}
