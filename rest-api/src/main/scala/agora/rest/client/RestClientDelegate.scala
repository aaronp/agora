package agora.rest.client

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer

import scala.concurrent.Future

class RestClientDelegate(underlying: RestClient) extends RestClient {
  override def send(request: HttpRequest): Future[HttpResponse] = underlying.send(request)

  override implicit def materializer: Materializer = underlying.materializer

  override def stop(): Future[Any] = underlying.stop()
}
