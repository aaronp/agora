package agora.api.exchange

import agora.api.worker._
import agora.api.{JobId, nextJobId, nextSubscriptionKey}
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

case class ExchangeState(subscriptionsById: Map[SubscriptionKey, (WorkSubscription, Requested)] = Map[SubscriptionKey, (WorkSubscription, Requested)](),
                         jobsById: Map[JobId, SubmitJob] = Map[JobId, SubmitJob]())
    extends StrictLogging {

  /** @param key the subscription key
    * @return the number of work items pending for the given subscription, or 0 if the subscription is unknown
    */
  private[exchange] def pending(key: SubscriptionKey): Int = {
    val remainingOpt = subscriptionsById.get(key).map(_._2).map { remaining =>
      remaining.remaining(this)
    }
    remainingOpt.getOrElse(0)
  }

  private[exchange] def updatePending(key: SubscriptionKey, delta: Int, seenCheck: Set[SubscriptionKey] = Set.empty): ExchangeState = {
    val newStateOpt = subscriptionsById.get(key).map {
      case (sub, FixedRequested(n)) =>
        val newRequested = FixedRequested((n + delta).max(0))
        copy(subscriptionsById = subscriptionsById.updated(key, (sub, newRequested)))
      case (_, LinkedRequested(ids)) =>
        val seen = seenCheck + key
        ids.foldLeft(this) {
          case (_, id) if seen.contains(id) =>
            sys.error(s"Circular reference detected with linked subscription from $key -> $id, seen: $seen")
          case (state, id) => state.updatePending(id, delta, seen + id)
        }
    }

    newStateOpt.getOrElse(this)
  }

  /**
    * Creates matches based on the given predicate.
    *
    * Composite matches are always considered first, as by definition if an entire composite match
    * matches then all of its constituent parts would as well.
    *
    * @param matcher
    * @return
    */
  def matches(implicit matcher: JobPredicate): (List[MatchNotification], ExchangeState) = {

    logger.trace(s"Checking for matches between ${jobsById.size} jobs and ${subscriptionsById.size} subscriptions")

    jobsById.foldLeft(List[MatchNotification]() -> this) {
      case (accumulator @ (matches, oldState), (jobId, job)) =>
        val candidates: Seq[Candidate] = oldState.workCandidatesForJob(jobId, job)
        val chosen: Seq[Candidate]     = job.submissionDetails.selection.select(candidates)

        if (chosen.isEmpty) {
          accumulator
        } else {
          val (thisMatch, newState) = oldState.createMatch(jobId, job, chosen)
          (thisMatch :: matches, newState)
        }
    }
  }

  /** @param jobId the job id belonging to the job to match
    * @param job   the job to match
    * @return a collection of subscription keys, subscriptions and the remaining items which would match the given job
    */
  private def workCandidatesForJob(jobId: JobId, job: SubmitJob)(implicit matcher: JobPredicate): CandidateSelection = {
    subscriptionsById.collect {
      case (id, (subscription, requested)) if requested.remaining(this) > 0 && job.matches(subscription) =>
        val newState  = updatePending(id, -1)
        val remaining = newState.pending(id)
        def check     = requested.remaining(this)
        assert(remaining == (check - 1), s"${remaining} != $check - 1 for $id in $this")
        Candidate(id, subscription, remaining)
    }.toSeq
  }

  private def createMatch(jobId: JobId, job: SubmitJob, chosen: CandidateSelection): (MatchNotification, ExchangeState) = {

    val newJobsById = jobsById - jobId
    val newState = chosen.foldLeft(copy(jobsById = newJobsById)) {
      case (state, Candidate(key, _, n)) =>
        val newState = state.updatePending(key, -1)
        newState
    }
    val notification = MatchNotification(jobId, job, chosen)
    notification -> newState
  }

  def cancelJobs(request: CancelJobs) = {
    val cancelled = request.ids.map { id =>
      id -> jobsById.contains(id)
    }
    val newJobsById = jobsById -- request.ids
    CancelJobsResponse(cancelled.toMap) -> copy(jobsById = newJobsById)
  }

  private def containsSubscription(id: SubscriptionKey) = subscriptionsById.contains(id)

  /** cancels the subscription IDs.
    *
    * @param ids the subscription IDs to cancel
    * @return a cancelled response and updated state
    */
  def cancelSubscriptions(ids: Set[SubscriptionKey]): (CancelSubscriptionsResponse, ExchangeState) = {
    val newSubscriptionsById = subscriptionsById -- ids
    val newState             = copy(subscriptionsById = newSubscriptionsById)

    val cancelled = ids.map { id =>
      val usedToContain = containsSubscription(id)
      if (usedToContain) {
        require(!newState.containsSubscription(id), s"$id wasn't actually cancelled")
      }
      id -> usedToContain
    }
    CancelSubscriptionsResponse(cancelled.toMap) -> newState
  }

  def queueState(request: QueueState): QueueStateResponse = {
    val foundJobs = jobsById.collect {
      case (_, job) if request.matchesJob(job) => job
    }
    val foundWorkers = subscriptionsById.collect {
      case (key, (sub, pending)) if request.matchesSubscription(sub.details.aboutMe) =>
        PendingSubscription(key, sub, pending.remaining(this))
    }

    QueueStateResponse(foundJobs.toList, foundWorkers.toList)
  }

  def subscribe(inputSubscription: WorkSubscription): (WorkSubscriptionAck, ExchangeState) = {
    val (id, subscription) = inputSubscription.key match {
      case Some(key) => (key, inputSubscription)
      case None =>
        val key             = nextSubscriptionKey()
        val newSubscription = inputSubscription.withSubscriptionKey(key)

        logger.debug(s"Created new subscription [${key}] $newSubscription")
        key -> newSubscription
    }

    val newState = subscriptionsById.get(id).map(_._2) match {
      case Some(_) => updateSubscription(id, inputSubscription.details)
      case None =>
        val requested            = Requested(inputSubscription.subscriptionReferences)
        val newSubscriptionsById = subscriptionsById.updated(id, subscription -> requested)
        copy(subscriptionsById = newSubscriptionsById)
    }
    WorkSubscriptionAck(id) -> newState
  }

  /**
    * Submits the given job to the state
    * @param inputJob the job to append to the state
    * @return the new state and job response, but not in that order
    */
  def submit(inputJob: SubmitJob): (SubmitJobResponse, ExchangeState) = {
    val (id, job) = inputJob.jobId match {
      case Some(id) => id -> inputJob
      case None =>
        val id = nextJobId()
        id -> inputJob.withId(id)
    }
    logger.debug(s"Adding job [$id] $job")
    val newJobsById = jobsById.updated(id, job)

    SubmitJobResponse(id) -> copy(jobsById = newJobsById)
  }

  def request(id: SubscriptionKey, n: Int): Try[(RequestWorkAck, ExchangeState)] = {
    require(n >= 0)
    subscriptionsById.get(id) match {
      case None => Failure(new Exception(s"subscription '$id' doesn't exist. Known ${subscriptionsById.size} subscriptions are: ${subscriptionsById.keySet.mkString(",")}"))
      case Some((_, before)) =>
        val newState = updatePending(id, n)
        Success(RequestWorkAck(id, before.remaining(this), newState.pending(id)) -> newState)
    }
  }

  private[exchange] def updateSubscription(id: SubscriptionKey, details: WorkerDetails): ExchangeState = {
    subscriptionsById.get(id) match {
      case Some((subscription, n)) =>
        val newSubscription = subscription.append(details.aboutMe)
        copy(subscriptionsById = subscriptionsById.updated(id, (newSubscription, n)))
      case None => this
    }
  }

}
