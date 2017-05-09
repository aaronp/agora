package jabroni.api.exchange

import com.typesafe.scalalogging.StrictLogging
import jabroni.api.worker.SubscriptionKey
import jabroni.api.{JobId, nextJobId}

import scala.collection.parallel.ParSeq
import scala.concurrent.Future

/**
  * An exchange supports both 'client' requests (e.g. offering and cancelling work to be done)
  * and work subscriptions
  */
trait Exchange extends JobPublisher { //with QueueObserver {

  def pull(req: SubscriptionRequest): Future[SubscriptionResponse] = {
    req match {
      case ws: WorkSubscription => subscribe(ws)
      case next: RequestWork => take(next)
    }
  }

  def subscribe(request: WorkSubscription) = pull(request).mapTo[WorkSubscriptionAck]

  def take(request: RequestWork) = pull(request).mapTo[RequestWorkAck]

  def take(id: SubscriptionKey, itemsRequested: Int): Future[RequestWorkAck] = take(RequestWork(id, itemsRequested))
}

object Exchange {


  def apply(onMatch: OnMatch = MatchObserver.apply())(implicit matcher: JobPredicate = JobPredicate()): Exchange with QueueObserver = new InMemory(onMatch)

  type Remaining = Int
  type Match = (SubmitJob, Seq[(SubscriptionKey, WorkSubscription, Remaining)])

  type OnMatch = Match => Unit

  class InMemory(onMatch: OnMatch)(implicit matcher: JobPredicate) extends Exchange with QueueObserver with StrictLogging {

    private object SubscriptionLock

    private var subscriptionsById = Map[SubscriptionKey, (WorkSubscription, Int)]()

    private def pending(key: SubscriptionKey) = SubscriptionLock.synchronized {
      subscriptionsById.get(key).map(_._2).getOrElse(0)
    }

    private var jobsById = Map[JobId, SubmitJob]()

    override def listJobs(request: QueuedJobs): Future[QueuedJobsResponse] = {
      val found = jobsById.collect {
        case (_, job) if request.matches(job) => job
      }
      val resp = QueuedJobsResponse(found.toList)
      Future.successful(resp)
    }

    override def listSubscriptions(request: ListSubscriptions): Future[ListSubscriptionsResponse] = SubscriptionLock.synchronized {
      val found = subscriptionsById.collect {
        case (key, (sub, pending)) if request.subscriptionCriteria.matches(sub.details.aboutMe) =>
          PendingSubscription(key, sub, pending)
      }
      val resp = ListSubscriptionsResponse(found.toList)
      Future.successful(resp)
    }

    override def subscribe(subscription: WorkSubscription) = {
      val id = subscription.key
      logger.debug(s"Creating new subscription [$id] $subscription")
      SubscriptionLock.synchronized {
        subscriptionsById = subscriptionsById.updated(id, subscription -> 0)
      }
      Future.successful(WorkSubscriptionAck(id))
    }

    override def take(request: RequestWork) = {
      val RequestWork(id, n) = request
      SubscriptionLock.synchronized {
        subscriptionsById.get(id) match {
          case None =>
            Future.failed(new Exception(s"subscription '$id' doesn't exist. Known ${subscriptionsById.size} subscriptions are: ${subscriptionsById.take(100).keySet.mkString(",")}"))
          case Some((_, before)) =>
            updatePending(id, before + n)

            // if there weren't any jobs previously, then we may be able to take some work
            if (before == 0) {
              triggerMatch()
            } else {
              logger.debug(s"Not triggering match for subscriptions increase on [$id]")
            }
            Future.successful(RequestWorkAck(id, pending(id)))
        }
      }
    }

    private def updatePending(id: SubscriptionKey, n: Int) = SubscriptionLock.synchronized {
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
      triggerMatch()
      Future.successful(SubmitJobResponse(id))
    }

    private def triggerMatch(): Unit = SubscriptionLock.synchronized {
      logger.trace(s"Checking for matches between ${jobsById.size} jobs and ${subscriptionsById.size} subscriptions")
      val newJobs = jobsById.filter {
        case (_, job) =>

          val candidates: ParSeq[(SubscriptionKey, WorkSubscription, Int)] = subscriptionsById.toSeq.par.collect {
            case (id, (subscription, requested)) if requested > 0 && job.matches(subscription) =>
              (id, subscription, (requested - 1).ensuring(_ >= 0))
          }

          val chosen = job.submissionDetails.selection.select(candidates.seq)
          if (chosen.isEmpty) {
            true
          } else {
            subscriptionsById = chosen.foldLeft(subscriptionsById) {
              case (map, (key, _, n)) =>
                require(n >= 0, s"Take cannot be negative ($key, $n)")
                val pear = map(key)._1
                map.updated(key, (pear, n))
            }
            logger.debug(s"Triggering match between $job and $chosen")
            onMatch(job, chosen)
            false // remove the job... it got sent somewhere
          }
      }
      jobsById = newJobs
    }
  }

}