package agora.rest.worker

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.HttpRequest
import agora.rest.CommonRequestBuilding

import scala.concurrent.ExecutionContext

object WorkerHttp extends CommonRequestBuilding {

  def apply[T: ToEntityMarshaller](path: String, request: T)(implicit ec: ExecutionContext): HttpRequest = {
    val fixedPath = if (path.startsWith("/")) path else s"/$path"
    Post(fixedPath, request).withCommonHeaders
  }

  val healthRequest: HttpRequest = Get("/rest/health")

}
