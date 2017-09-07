package agora.rest.client
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer

import scala.concurrent.Future

case class RoundRobinClient(pool : Iterable[RestClient], retryStrategy: RetryStrategy) extends RestClient {
  require(pool.nonEmpty, "No clients specified")
  private val cycle: Iterator[RestClient] = Iterator.continually(pool).flatMap(_.iterator)

  private val underlying = RetryClient(retryStrategy) { () =>
    cycle.next
  }
  override def send(request: HttpRequest): Future[HttpResponse] = {
    underlying.send(request)
  }

  override implicit def materializer: Materializer = underlying.materializer
}

object RoundRobinClient {

}