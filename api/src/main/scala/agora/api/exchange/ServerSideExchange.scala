package agora.api.exchange

import agora.api.exchange.observer.ExchangeObserverDelegate
import agora.api.nextJobId

import scala.concurrent.{ExecutionContext, Future}

/**
  * Adds a special type for local exchanges. also exposing a means to observe jobs.
  *
  * This way the 'MatchObserver' provided to the Exchange is accessible. Also, as convenient
  * as it is to provide multiple implementations of a generic [[Exchange]] (i.e. local and remote),
  * It's useful to know the intent when wiring together components (e.g. so we don't have a REST service
  * configured which just sends exchange requests to itself)
  *
  * @param underlying
  * @param observer
  */
case class ServerSideExchange(underlying: Exchange, val observer: ExchangeObserverDelegate = ExchangeObserverDelegate())(implicit ec: ExecutionContext)
    extends Exchange
    with AutoCloseable {

  override def onClientRequest(request: ClientRequest) = underlying.onClientRequest(request)

  override def onSubscriptionRequest(req: SubscriptionRequest) =
    underlying.onSubscriptionRequest(req)

  override def submit(req: SubmitJob): Future[ClientResponse] = {
    if (req.submissionDetails.awaitMatch) {
      submitJobAndAwaitMatch(req)
    } else {
      super.submit(req)
    }
  }

  /**
    * Submits the job to the exchange and blocks until there's a match
    *
    * @param submitJob
    * @return a future BlockSubmitJobResponse
    */
  def submitJobAndAwaitMatch(submitJob: SubmitJob)(implicit submitCtxt: ExecutionContext): Future[BlockingSubmitJobResponse] = {
    val jobWithId                                      = submitJob.jobId.fold(submitJob.withId(nextJobId()))(_ => submitJob)
    val matchFuture: Future[BlockingSubmitJobResponse] = observer.awaitJob(jobWithId)(submitCtxt)
    underlying.submit(jobWithId)
    matchFuture
  }

  override def close(): Unit = underlying match {
    case auto: AutoCloseable => auto.close()
    case _                   =>
  }
}

object ServerSideExchange {
  def apply(): ServerSideExchange = {
    import ExecutionContext.Implicits._
    implicit val matcher: JobPredicate = JobPredicate()
    val obs                            = ExchangeObserverDelegate()
    val exchange                       = Exchange(obs)
    new ServerSideExchange(exchange, obs)
  }
}
