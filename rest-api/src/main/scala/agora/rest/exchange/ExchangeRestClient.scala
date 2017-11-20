package agora.rest.exchange

import agora.api.exchange._
import agora.api.worker.{SubscriptionKey, WorkerDetails}
import agora.rest.client.{RestClient, RetryClient}
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

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
class ExchangeRestClient(val rest: RestClient) extends Exchange with FailFastCirceSupport with AutoCloseable with StrictLogging {

  override def toString = s"ExchangeRestClient($rest)"

  import RestClient.implicits._

  implicit def execContext = rest.executionContext

  implicit def materializer = rest.materializer

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
          logger.error(s"$client retrying after getting response w/ '${resp.status}' $err ($bodyOpt). Checking the queue...", err)
          client.reset(Option(err))
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

  override def updateSubscriptionDetails(update: UpdateSubscription): Future[UpdateSubscriptionAck] = {
    rest.send(ExchangeHttp(update)).flatMap { exchangeResp =>
      exchangeResp.as[UpdateSubscriptionAck](retryOnError(updateSubscriptionDetails(update)))
    }
  }

  override def request(requestWork: RequestWork): Future[RequestWorkAck] = {
    rest.send(ExchangeHttp(requestWork)).flatMap(_.as[RequestWorkAck](retryOnError(request(requestWork))))
  }

  override def submit(job: SubmitJob): Future[ClientResponse] = {

    def sendBlocking: Future[BlockingSubmitJobResponse] = {
      rest.send(ExchangeHttp(job)).flatMap { exchangeResp =>
        exchangeResp.as[BlockingSubmitJobResponse](retryOnError(sendBlocking))
      }
    }

    def sendAsync: Future[SubmitJobResponse] = {
      rest.send(ExchangeHttp(job)).flatMap { exchangeResp =>
        exchangeResp.as[SubmitJobResponse](retryOnError(sendAsync))
      }
    }

    if (job.submissionDetails.awaitMatch) {
      sendBlocking
    } else {
      sendAsync
    }
  }

  override def queueState(request: QueueState = QueueState()): Future[QueueStateResponse] = {
    rest.send(ExchangeHttp(request)).flatMap(_.as[QueueStateResponse](retryOnError(queueState(request))))
  }

  override def close(): Unit = stop()

  def stop() = rest.stop()

  override def cancelJobs(request: CancelJobs): Future[CancelJobsResponse] = {
    rest.send(ExchangeHttp(request)).flatMap(_.as[CancelJobsResponse](retryOnError(cancelJobs(request))))
  }

  override def cancelSubscriptions(request: CancelSubscriptions): Future[CancelSubscriptionsResponse] = {
    rest
      .send(ExchangeHttp(request))
      .flatMap(_.as[CancelSubscriptionsResponse](retryOnError(cancelSubscriptions(request))))
  }
}

object ExchangeRestClient {

  def apply(rest: RestClient): ExchangeRestClient = new ExchangeRestClient(rest)

}
