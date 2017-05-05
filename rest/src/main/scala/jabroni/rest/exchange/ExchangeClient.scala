package jabroni.rest.exchange

import akka.stream.Materializer
import jabroni.api.exchange._
import jabroni.rest.client.RestClient

import scala.concurrent.{ExecutionContext, Future}

class ExchangeClient(rest: RestClient)(implicit mat: Materializer) extends Exchange {

  import RestClient.implicits._

  override def subscribe(request: WorkSubscription) = rest.send(ExchangeHttp(request)).flatMap(_.as[WorkSubscriptionAck])

  override def take(request: RequestWork) = rest.send(ExchangeHttp(request)).flatMap(_.as[RequestWorkAck])

  override def submit(submit: SubmitJob) = rest.send(ExchangeHttp(submit)).flatMap(_.as[SubmitJobResponse])

  override def listJobs(request: QueuedJobs) = {
    rest.send(ExchangeHttp(request)).flatMap(_.as[QueuedJobsResponse])
  }

  override def listSubscriptions(request: ListSubscriptions) = {
    rest.send(ExchangeHttp(request)).flatMap(_.as[ListSubscriptionsResponse])
  }
}

object ExchangeClient {
  def apply(rest: RestClient)(implicit mat: Materializer): Exchange = new ExchangeClient(rest)

}