package jabroni.rest.exchange

import akka.stream.Materializer
import jabroni.api.exchange._
import jabroni.rest.client.RestClient

import scala.concurrent.{ExecutionContext, Future}

class ExchangeClient(rest: RestClient)(implicit ec: ExecutionContext, mat: Materializer) extends Exchange {
  import RestClient.implicits._

  override def pull(request: SubscriptionRequest): Future[SubscriptionResponse] = {
    request match {
      case subscribe: WorkSubscription =>
        rest.send(ExchangeHttp(subscribe)).flatMap(_.as[WorkSubscriptionAck])
      case take: RequestWork =>
        rest.send(ExchangeHttp(take)).flatMap(_.as[RequestWorkAck])
    }
  }

  override def send(request: ClientRequest): Future[ClientResponse] = {
    request match {
      case submit: SubmitJob => rest.send(ExchangeHttp(submit)).flatMap(_.as[SubmitJobResponse])
    }
  }
}

object ExchangeClient {

  def apply(rest: RestClient)(implicit ec: ExecutionContext, mat: Materializer): Exchange = new ExchangeClient(rest)

}