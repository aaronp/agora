package agora.api.exchange

import com.typesafe.scalalogging.StrictLogging
import agora.api._
import agora.api.worker.SubscriptionKey

import scala.collection.parallel.ParSeq
import scala.concurrent.Future

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
    * Submit a job to the exchange. If 'awaitMatch' is specified on the SubmitJob then the response
    * will be a [[BlockingSubmitJobResponse]]. Otherwise it will just be sent to the exchange an immediately
    * return a [[SubmitJobResponse]].
    *
    * @param req
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
      case ws: WorkSubscription => subscribe(ws)
      case next: RequestWork    => take(next)
    }
  }

  def subscribe(request: WorkSubscription) = onSubscriptionRequest(request).mapTo[WorkSubscriptionAck]

  def take(request: RequestWork) = onSubscriptionRequest(request).mapTo[RequestWorkAck]

  def take(id: SubscriptionKey, itemsRequested: Int): Future[RequestWorkAck] = take(RequestWork(id, itemsRequested))
}

object Exchange {

  /**
    * Creates a new, in-memory exchange with the given job/worker match notifier
    * @param onMatch the observer notified when a job is paired with a worker subscription
    * @param matcher the match logic used to pair work with subscriptions
    * @return a new Exchange instance
    */
  def apply(onMatch: OnMatch)(implicit matcher: JobPredicate): Exchange = new InMemory(onMatch)

  def instance(): Exchange = apply(MatchObserver())(JobPredicate())

  type Remaining = Int
  type Match     = (SubmitJob, Seq[(SubscriptionKey, WorkSubscription, Remaining)])

  type OnMatch = Match => Unit

  /**
    * A default, ephemeral, non-thread-safe implementation of an exchange
    *
    * @param onMatch
    * @param matcher
    */
  class InMemory(onMatch: OnMatch)(implicit matcher: JobPredicate) extends Exchange with StrictLogging {

    private var subscriptionsById = Map[SubscriptionKey, (WorkSubscription, Int)]()

    private def pending(key: SubscriptionKey) = {
      subscriptionsById.get(key).map(_._2).getOrElse(0)
    }

    private var jobsById = Map[JobId, SubmitJob]()

    override def queueState(request: QueueState): Future[QueueStateResponse] = {
      val foundJobs = jobsById.collect {
        case (_, job) if request.matchesJob(job) => job
      }
      val foundWorkers = subscriptionsById.collect {
        case (key, (sub, pending)) if request.matchesSubscription(sub.details.aboutMe) =>
          PendingSubscription(key, sub, pending)
      }

      Future.successful(QueueStateResponse(foundJobs.toList, foundWorkers.toList))
    }

    override def subscribe(inputSubscription: WorkSubscription) = {
      val (id, subscription) = inputSubscription.key match {
        case Some(key) => (key, inputSubscription)
        case None =>
          val key = nextSubscriptionKey()
          key -> inputSubscription.withDetails(_.withSubscriptionKey(key))
      }

      logger.debug(s"Creating new subscription [$id] $subscription")
      subscriptionsById = subscriptionsById.updated(id, subscription -> 0)
      Future.successful(WorkSubscriptionAck(id))
    }

    override def take(request: RequestWork) = {
      val RequestWork(id, n) = request
      subscriptionsById.get(id) match {
        case None =>
          Future.failed(new Exception(s"subscription '$id' doesn't exist. Known ${subscriptionsById.size} subscriptions are: ${subscriptionsById.take(100).keySet.mkString(",")}"))
        case Some((_, before)) =>
          updatePending(id, before + n)

          // if there weren't any jobs previously, then we may be able to take some work
          if (before == 0) {
            checkForMatches()
          } else {
            logger.debug(s"Not triggering match for subscriptions increase on [$id]")
          }
          Future.successful(RequestWorkAck(id, pending(id)))
      }
    }

    private def updatePending(id: SubscriptionKey, n: Int) = {
      subscriptionsById = subscriptionsById.get(id).fold(subscriptionsById) {
        case (sub, before) =>
          logger.debug(s"changing pending subscriptions for [$id] from $before to $n")
          subscriptionsById.updated(id, sub -> n)
      }
    }

    override def submit(inputJob: SubmitJob) = {
      val (id, job) = inputJob.jobId match {
        case Some(id) => id -> inputJob
        case None =>
          val id = nextJobId()
          id -> inputJob.withId(id)
      }
      logger.debug(s"Adding job [$id] $job")
      jobsById = jobsById.updated(id, job)
      checkForMatches()

      Future.successful(SubmitJobResponse(id))
    }

    def checkForMatches() = {
      val (subscriptions, notifications) = triggerMatch(jobsById, subscriptionsById)
      if (notifications.nonEmpty) {
        jobsById = jobsById -- notifications.map(_.id)
        subscriptionsById = subscriptions
      }

      logger.trace(s"Checking for matches between ${jobsById.size} jobs and ${subscriptionsById.size} subscriptions")

      notifications.foreach {
        case MatchNotification(id, job, chosen) =>
          logger.debug(s"Triggering match between $job and $chosen")
          onMatch((job, chosen))
      }
    }

    override def cancelJobs(request: CancelJobs): Future[CancelJobsResponse] = {
      val cancelled = request.ids.map { id =>
        id -> jobsById.contains(id)
      }
      jobsById = jobsById -- request.ids
      Future.successful(CancelJobsResponse(cancelled.toMap))
    }

    override def cancelSubscriptions(request: CancelSubscriptions): Future[CancelSubscriptionsResponse] = {
      val cancelled = request.ids.map { id =>
        id -> subscriptionsById.contains(id)
      }
      subscriptionsById = subscriptionsById -- request.ids
      Future.successful(CancelSubscriptionsResponse(cancelled.toMap))
    }
  }

  private case class MatchNotification(id: JobId, job: SubmitJob, chosen: Seq[(SubscriptionKey, WorkSubscription, Remaining)])

  private def triggerMatch(jobQueue: Map[JobId, SubmitJob], initialSubscriptionQueue: Map[SubscriptionKey, (WorkSubscription, Int)])(
      implicit matcher: JobPredicate): (Map[SubscriptionKey, (WorkSubscription, Remaining)], List[MatchNotification]) = {
    jobQueue.foldLeft(initialSubscriptionQueue -> List[MatchNotification]()) {
      case ((subscriptionQueue, matches), (jobId, job)) =>
        val candidates: ParSeq[(SubscriptionKey, WorkSubscription, Int)] = subscriptionQueue.toSeq.par.collect {
          case (id, (subscription, requested)) if requested > 0 && job.matches(subscription) =>
            (id, subscription, (requested - 1).ensuring(_ >= 0))
        }

        val chosen: Seq[(SubscriptionKey, WorkSubscription, Remaining)] = job.submissionDetails.selection.select(candidates.seq)
        if (chosen.isEmpty) {
          (subscriptionQueue, matches)
        } else {
          val newSubscriptionQueue = chosen.foldLeft(subscriptionQueue) {
            case (map, (key, _, n)) =>
              require(n >= 0, s"Take cannot be negative ($key, $n)")
              val pear = map(key)._1
              map.updated(key, (pear, n))
          }
          val newMatches = MatchNotification(jobId, job, chosen) :: matches
          (newSubscriptionQueue, newMatches)
        }
    }
  }
}
