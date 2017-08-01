package agora.api.exchange

import agora.api._
import agora.api.worker.SubscriptionKey
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * An exchange supports both 'client' requests (e.g. offering and cancelling work to be done)
  * and work subscriptions
  */
trait Exchange {

  def onClientRequest(request: ClientRequest): Future[ClientResponse] = request match {
    case req: SubmitJob           => submit(req)
    case req: QueueState          => queueState(req)
    case req: CancelJobs          => cancelJobs(req)
    case req: CancelSubscriptions => cancelSubscriptions(req)
  }

  /**
    * Submit a job to the exchange and trigger a match for work.
    *
    * If 'awaitMatch' is specified on the SubmitJob then the response
    * will be a [[BlockingSubmitJobResponse]]. Otherwise it will just be sent to the exchange an immediately
    * return a [[SubmitJobResponse]].
    *
    * If the submitted job already contains a jobId, then that id will be used and any existing submission with
    * the same Id will be replaced.
    *
    * @param req the job request
    * @return either [[BlockingSubmitJobResponse]] or a [[SubmitJobResponse]]
    */
  def submit(req: SubmitJob): Future[ClientResponse] = onClientRequest(req)

  /**
    * Queue the state of the exchange
    *
    * @param request
    * @return the current queue state
    */
  def queueState(request: QueueState = QueueState()): Future[QueueStateResponse] = {
    onClientRequest(request).mapTo[QueueStateResponse]
  }

  /**
    * Cancels the submitted jobs, removing them from the exchange
    * @param request the request containing the jobs to cancel
    * @return a response containing a map between the input job Ids and a flag depicting if they were successfully cancelled
    */
  def cancelJobs(request: CancelJobs): Future[CancelJobsResponse] = {
    onClientRequest(request).mapTo[CancelJobsResponse]
  }

  final def cancelJobs(job: JobId, theRest: JobId*): Future[CancelJobsResponse] = {
    cancelJobs(CancelJobs(theRest.toSet + job))
  }

  def cancelSubscriptions(request: CancelSubscriptions): Future[CancelSubscriptionsResponse] = {
    onClientRequest(request).mapTo[CancelSubscriptionsResponse]
  }

  final def cancelSubscriptions(id: SubscriptionKey, theRest: SubscriptionKey*): Future[CancelSubscriptionsResponse] = {
    cancelSubscriptions(CancelSubscriptions(theRest.toSet + id))
  }

  def onSubscriptionRequest(req: SubscriptionRequest): Future[SubscriptionResponse] = {
    req match {
      case msg: WorkSubscription => subscribe(msg)
      case msg: RequestWork      => take(msg)
      case msg: Compose          => compose(msg)
    }
  }

  /** creates a new subscription based on the input subscriptions.
    *
    * When a 'take' request is made for the returned subscription, a 'take' is issued
    * for each of the subscriptions.
    *
    * When a request is received with 'submit', composite subscriptions will match only if all their composite subscriptions
    * match. It's for this reason that composite subscriptions have to be considered first, since by definition if a
    * composite subscription matches then any of the constituent subscriptions would match.
    *
    * @param compose the subscriptions to combine
    * @return a new subscription which composes the input subscriptions
    */
  def compose(compose: Compose): Future[WorkSubscriptionAck] = onSubscriptionRequest(compose).mapTo[WorkSubscriptionAck]

  /**
    * Creates a new [[WorkSubscription]], whos returned [[WorkSubscriptionAck]] key can be used to pull work items
    * from the exchange.
    *
    * @param request the work subscription
    * @return an ack containing the key needed to request work items
    */
  def subscribe(request: WorkSubscription) = onSubscriptionRequest(request).mapTo[WorkSubscriptionAck]

  /** @param request the number of work items to request
    * @return an ack which contains the current known total items requested
    */
  def take(request: RequestWork) = onSubscriptionRequest(request).mapTo[RequestWorkAck]

  /** convenience method for pulling work items
    */
  def take(id: SubscriptionKey, itemsRequested: Int): Future[RequestWorkAck] = take(RequestWork(id, itemsRequested))
}

object Exchange {

  /**
    * Creates a new, in-memory exchange with the given job/worker match notifier
    *
    * @param onMatch the observer notified when a job is paired with a worker subscription
    * @param matcher the match logic used to pair work with subscriptions
    * @return a new Exchange instance
    */
  def apply(onMatch: OnMatch)(implicit matcher: JobPredicate): Exchange = new Instance(new ExchangeState(), onMatch)

  def instance(): Exchange = apply(MatchObserver())(JobPredicate())

  type Match = (SubmitJob, Seq[Candidate])

  type OnMatch = Match => Unit

  /**
    * A default, ephemeral, non-thread-safe implementation of an exchange
    *
    * @param onMatch
    * @param matcher
    */
  class Instance(initialState: ExchangeState, onMatch: OnMatch)(implicit matcher: JobPredicate) extends Exchange with StrictLogging {

    private var state = initialState

    override def queueState(request: QueueState): Future[QueueStateResponse] = {
      Future.fromTry(Try(state.queueState(request)))
    }

    override def subscribe(inputSubscription: WorkSubscription) = {
      val (ack, newState) = state.subscribe(inputSubscription)
      state = newState
      Future.successful(ack)
    }

    override def take(request: RequestWork) = {
      val RequestWork(id, n) = request
      state.subscriptionsById.get(id) match {
        case None =>
          Future.failed(
            new Exception(s"subscription '$id' doesn't exist. Known ${state.subscriptionsById.size} subscriptions are: ${state.subscriptionsById.take(100).keySet.mkString(",")}"))
        case Some((_, before)) =>
          state = state.updatePending(id, before + n)

          // if there weren't any jobs previously, then we may be able to take some work
          if (before == 0) {
            checkForMatches()
          } else {
            logger.debug(s"Not triggering match for subscriptions increase on [$id]")
          }
          Future.successful(RequestWorkAck(id, state.pending(id)))
      }
    }

    override def compose(request: Compose): Future[WorkSubscriptionAck] = {
      state.addCompositeSubscription(request) match {
        case Success((ack, newState)) =>
          state = newState
          Future.successful(ack)
        case Failure(err) => Future.failed(err)
      }
    }

    override def submit(inputJob: SubmitJob) = {
      val (ack, newState) = state.submit(inputJob)
      state = newState
      checkForMatches()
      Future.successful(ack)
    }

    /**
      * Checks the jobs against the work subscriptions for matches using
      */
    def checkForMatches(): Unit = {

      val (notifications, newState) = state.matches
      state = newState

      notifications.foreach {
        case MatchNotification(id, job, chosen) =>
          logger.debug(s"Triggering match between $job and $chosen")
          onMatch((job, chosen))
      }
    }

    override def cancelJobs(request: CancelJobs): Future[CancelJobsResponse] = {
      val (resp, newState) = state.cancelJobs(request)
      state = newState
      Future.successful(resp)
    }

    override def cancelSubscriptions(request: CancelSubscriptions): Future[CancelSubscriptionsResponse] = {
      val (resp, newState) = state.cancelSubscriptions(request.ids)
      state = newState
      Future.successful(resp)
    }
  }

}
