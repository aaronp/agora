package agora.rest.exchange

import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import agora.api.`match`.MatchDetails
import agora.api.exchange._
import agora.api.worker.{HostLocation, WorkerDetails}
import agora.rest.client.{RestClient, RetryClient}
import agora.rest.exchange.ExchangeClient._
import agora.rest.worker.WorkerClient

import scala.concurrent.Future

/**
  * Represents a something that will request work and get a response.
  *
  * A normal workflow would be to request work from an exchange, have that work eventually
  * matched with a worker, and then receive a 307 response, telling us where to go.
  *
  * We then make a request (typically the original one, but perhaps not if it was e.g. a multipart request w/
  * a large upload or summat) to that worker.
  *
  * @param rest
  */
class ExchangeClient(val rest: RestClient, mkWorker: HostLocation => Dispatch) extends Exchange with RoutingClient with FailFastCirceSupport with AutoCloseable with StrictLogging {

  import RestClient.implicits._

  implicit def execContext = rest.executionContext

  implicit def materializer = rest.materializer

  type JobResponse = Future[_ <: ClientResponse]

  /**
    * What an odd signature!
    *
    * This is to match the 'as' function used to map http responses to some type 'T'.
    *
    * in the event of a failure (exception or non-success server response), we can optionally
    * retry. The 'retry' function is given first so that the second param list matches that
    * of the 'as' result
    *
    * @param retry
    * @tparam A
    * @return
    */
  protected def retryOnError[A](retry: => Future[A]): HandlerError => Future[A] = {
    rest match {
      case client: RetryClient =>
        handlerErr: HandlerError =>
          val (bodyOpt, resp, err) = handlerErr
          client.reset()
          logger.error(s"Client retrying after getting response w/ '${resp.status}' $err ($bodyOpt). Checking the queue...", err)
          queueState().flatMap { state =>
            logger.error(state.description, err)
            retry
          }
      case _ =>
        handlerErr: HandlerError =>
          val (_, resp, err) = handlerErr
          logger.error(s"Can't retry w/ $rest client after getting response w/ '${resp.status}' : $err")
          queueState().foreach { state =>
            logger.error(state.description, err)
          }
          throw err
    }
  }

  override def subscribe(request: WorkSubscription): Future[WorkSubscriptionAck] = {
    rest.send(ExchangeHttp(request)).flatMap { exchangeResp =>
      exchangeResp.as[WorkSubscriptionAck](retryOnError(subscribe(request)))
    }
  }

  override def take(request: RequestWork): Future[RequestWorkAck] = {
    rest.send(ExchangeHttp(request)).flatMap(_.as[RequestWorkAck](retryOnError(take(request))))
  }

  override def submit(submit: SubmitJob) = {
    enqueueAndDispatch(submit)(_.sendRequest(submit.job))._1
  }

  protected def clientFor(location: HostLocation): Dispatch = mkWorker(location)

  override def queueState(request: QueueState = QueueState()): Future[QueueStateResponse] = {
    rest.send(ExchangeHttp(request)).flatMap(_.as[QueueStateResponse](retryOnError(queueState(request))))
  }

  override def close(): Unit = rest.close()

  override def cancelJobs(request: CancelJobs): Future[CancelJobsResponse] = {
    import io.circe.generic.auto._
    rest.send(ExchangeHttp(request)).flatMap(_.as[CancelJobsResponse](retryOnError(cancelJobs(request))))
  }

  override def cancelSubscriptions(request: CancelSubscriptions): Future[CancelSubscriptionsResponse] = {
    import io.circe.generic.auto._
    rest.send(ExchangeHttp(request)).flatMap(_.as[CancelSubscriptionsResponse](retryOnError(cancelSubscriptions(request))))
  }
}

object ExchangeClient {

  type Dispatch = (String, MatchDetails, WorkerDetails) => WorkerClient

  def apply(rest: RestClient)(mkWorker: HostLocation => Dispatch): ExchangeClient = new ExchangeClient(rest, mkWorker)

}
