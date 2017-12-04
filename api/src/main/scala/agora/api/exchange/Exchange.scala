package agora.api.exchange

import agora.api._
import agora.api.exchange.dsl.JobSyntax
import agora.api.exchange.instances.{ExchangeInstance, ExchangeState}
import agora.api.exchange.observer.{ExchangeObserver, ExchangeObserverDelegate}
import agora.api.worker.SubscriptionKey

import scala.concurrent.{ExecutionContext, Future}

/**
  * The exchange is where 'SubmitJob' elements are enqueued and matched against [[WorkSubscription]]s requesting work.
  *
  * On each job enqueued or work subscription created (or updated), we check each for a 'match' and notify
  * an observer that we've crossed work w/ a work subscription.
  *
  * Both the job and the work subscription contain opaque blocks of data which the other can match by specifying
  * some kind of criteria. It's only when the work subscription matches the job AND the job matches a work subscription
  * that we consider a match successful.
  *
  * Also, any number of work subscriptions can match a job, and so it's the [[SubmissionDetails]] selection which decides
  * which workers to choose (first, all, based on some criteria, etc).
  *
  * Also of note is that work subscriptions pull work -- they each have some concept of a number of work items requested
  * as per reactive streams. When a match is successful, the exchange will decrement the requested work before notifying
  * of the match. By doing this eagerly in the exchange we get around the problem of complicated handshakes between
  * the thing submitting the job and the worker requesting the work (e.g. having to lock/ack/etc).
  *
  * It is the responsibility of the [[ExchangeObserver]] to do something with the matched work -- the exchange is
  * simply the mechanism for matching up enqueued [[SubmitJob]] requests with [[WorkSubscription]]s.
  */
trait Exchange extends JobSyntax {

  override protected def exchange = this

  /**
    * A convenience method where client requests can be sent through this single function which
    * delegates to the appropriate method. The 'convenience' in this sense is in terms of
    * pluggability into message handling systems such as actor systems, REST endpoints, etc.
    *
    * @param request
    * @return the ClientResponse
    */
  def onClientRequest(request: ClientRequest): Future[ClientResponse] = request match {
    case req: SubmitJob           => submit(req)
    case req: QueueState          => queueState(req)
    case req: CancelJobs          => cancelJobs(req)
    case req: CancelSubscriptions => cancelSubscriptions(req)
  }

  /**
    * A convenience method where subscription requests can be sent through this single function which
    * delegates to the appropriate method. The 'convenience' in this sense is in terms of
    * plugability into message handling systems such as actor systems, REST endpoints, etc.
    *
    * @param subscriptionRequest the subscription request
    * @return the SubscriptionResponse
    */
  def onSubscriptionRequest(subscriptionRequest: SubscriptionRequest): Future[SubscriptionResponse] = {
    subscriptionRequest match {
      case msg: WorkSubscription   => subscribe(msg)
      case msg: RequestWork        => request(msg)
      case msg: UpdateSubscription => updateSubscriptionDetails(msg)
    }
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
    * Creates or updates a [[WorkSubscription]], whose returned [[WorkSubscriptionAck]] key can be used to pull work items
    * from the exchange.
    *
    * If the request specifies a subscription key then any existing subscription with the given id will be updated by
    * having its work details combined w/ the existing subscription
    *
    * If no subscription key is supplied, then a new one will be generated and provided on the ack.
    *
    * @param request the work subscription
    * @return an ack containing the key needed to request work items
    */
  def subscribe(request: WorkSubscription): Future[WorkSubscriptionAck] = {
    onSubscriptionRequest(request).mapTo[WorkSubscriptionAck]
  }

  /**
    * Convenience method to subscribe and immediately request work items
    *
    * @param workSubscription the work subscription
    * @param initialRequest   the number of work items to request
    * @param ec               the execution context
    * @return a tuple of the subscribe ack and request ack
    */
  def subscribe(workSubscription: WorkSubscription, initialRequest: Int)(implicit ec: ExecutionContext): Future[(WorkSubscriptionAck, RequestWorkAck)] = {
    subscribe(workSubscription).flatMap { ack =>
      request(ack.id, initialRequest).map { takeAck =>
        ack -> takeAck
      }
    }
  }

  /**
    * Updates the json subscription details referred to by the subscription key.
    *
    * @see [[UpdateSubscription]] for comments
    */
  def updateSubscriptionDetails(update: UpdateSubscription): Future[UpdateSubscriptionAck] = {
    onSubscriptionRequest(update).mapTo[UpdateSubscriptionAck]
  }

  /** @param requestWork the number of work items to request
    * @return an ack which contains the current known total items requested
    */
  @deprecated("use 'request'", "0.34")
  def take(requestWork: RequestWork) = request(requestWork)

  /** @param requestWork the number of work items to request
    * @return an ack which contains the current known total items requested
    */
  def request(requestWork: RequestWork) = onSubscriptionRequest(requestWork).mapTo[RequestWorkAck]

  /** convenience method for pulling work items
    */
  final def request(id: SubscriptionKey, itemsRequested: Int): Future[RequestWorkAck] =
    request(RequestWork(id, itemsRequested))

  /** convenience method for pulling work items
    */
  @deprecated("use 'request'", "0.34")
  final def take(id: SubscriptionKey, itemsRequested: Int): Future[RequestWorkAck] =
    take(RequestWork(id, itemsRequested))

  /**
    * Queue the state of the exchange
    *
    * @param query the queue criteria
    * @return the current queue state
    */
  def queueState(query: QueueState = QueueState()): Future[QueueStateResponse] = {
    onClientRequest(query).mapTo[QueueStateResponse]
  }

  /**
    * Cancels the submitted jobs, removing them from the exchange
    *
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
}

object Exchange {

  /**
    * Creates a new, in-memory exchange with the given job/worker match notifier
    *
    * @param observer the observer notified when a job is paired with a worker subscription
    * @param matcher  the match logic used to pair work with subscriptions
    * @return a new Exchange instance
    */
  def apply(observer: ExchangeObserver)(implicit matcher: JobPredicate = JobPredicate()) =
    new ExchangeInstance(new ExchangeState(observer))

  def instance(): Exchange = apply(ExchangeObserverDelegate())(JobPredicate())

}
