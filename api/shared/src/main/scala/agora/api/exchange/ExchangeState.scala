package agora.api.exchange

import agora.api.worker._
import agora.api.{JobId, nextJobId, nextSubscriptionKey}
import com.typesafe.scalalogging.StrictLogging

import scala.util.Try

case class ExchangeState(subscriptionsById: Map[SubscriptionKey, (WorkSubscription, Int)] = Map[SubscriptionKey, (WorkSubscription, Int)](),
                         compositeSubscriptionsById: Map[SubscriptionKey, Compose] = Map[SubscriptionKey, Compose](),
                         jobsById: Map[JobId, SubmitJob] = Map[JobId, SubmitJob]())
    extends StrictLogging {

  import ExchangeState._

  // validate subscription
  {
    val intersection = compositeSubscriptionsById.keySet &~ subscriptionsById.keySet
    require(intersection.isEmpty, s"$intersection is both a composite and a normal subscription")
  }

  def pending(key: SubscriptionKey): Int = {
    subscriptionsById.get(key).map(_._2).getOrElse(0)
  }

  private lazy val resolvedCompositeSubscriptionsById: Map[SubscriptionKey, (Compose, Set[SubscriptionKey])] = {
    compositeSubscriptionsById.map {
      case (compositeId, compose) =>
        val ids        = flattenSubscriptions(compositeSubscriptionsById, compositeId)
        val invalidIds = ids.filterNot(subscriptionsById.contains)
        require(invalidIds.isEmpty, s"$compositeId refers to invalid subscription id(s) $invalidIds")
        compositeId -> (compose, ids)
    }
  }

  def findCompositeCandidate(job: SubmitJob, candidates: CandidateSelection)(implicit matcher: JobPredicate): Option[(SubscriptionKey, Compose, CandidateSelection)] = {
    val candidateIds = candidates.map(_._1)
    val compositeMatchOpt = resolvedCompositeSubscriptionsById.collectFirst {
      case (composeId, (compose, ids)) if job.matches(compose.subscription) && ids.forall(candidateIds.contains) =>
        logger.debug(s"Composite subscription '$composeId' matches")
        val compositeCandidates = candidates.filter {
          case (id, _, _) => ids.contains(id)
        }

        (composeId, compose, compositeCandidates)
    }

    compositeMatchOpt
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
        val candidates: Seq[Candidate] = workCandidatesForJob(jobId, job, oldState.subscriptionsById)
        findCompositeCandidate(job, candidates) match {
          case Some((compositeId, composite, filtered)) =>
            val (_, newState)         = createMatch(jobId, job, filtered)
            val minRemaining          = filtered.map(_._3).min
            val candidateSelection    = List((compositeId, composite.subscription, minRemaining))
            val candidateNotification = MatchNotification(jobId, job, candidateSelection)
            (candidateNotification :: matches) -> newState
          case None =>
            val chosen: Seq[Candidate] = job.submissionDetails.selection.select(candidates)

            if (chosen.isEmpty) {
              accumulator
            } else {
              val (thisMatch, newState) = createMatch(jobId, job, chosen)
              (thisMatch :: matches, newState)
            }
        }
    }
  }

  /** tries to remove the job from the given state and update the pending subscriptions' 'remaining' count
    *
    * @param choose  a function to narrow down candidates for a single job match
    * @param jobId   the job id for the input job
    * @param job     the job to match
    * @param matcher the matching logic
    * @return an optional new state and match notification should the job match
    */
  private def matchesForJob(jobId: JobId, job: SubmitJob)(choose: CandidateSelection => Seq[Candidate])(
      implicit matcher: JobPredicate): Option[(MatchNotification, ExchangeState)] = {
    require(jobsById.contains(jobId), s"Bug: Job map doesn't contain $jobId")
    val candidates: Seq[Candidate] = workCandidatesForJob(jobId, job, subscriptionsById)

    val chosen: Seq[Candidate] = choose(candidates)

    if (chosen.isEmpty) {
      None
    } else {
      val newMatch: (MatchNotification, ExchangeState) = createMatch(jobId, job, chosen)
      Option(newMatch)
    }
  }

  private def createMatch(jobId: JobId, job: SubmitJob, chosen: CandidateSelection): (MatchNotification, ExchangeState) = {
    val newSubscriptionQueue = chosen.foldLeft(subscriptionsById) {
      case (map, (key, _, n)) =>
        require(n >= 0, s"Take cannot be negative ($key, $n)")
        val pear = map(key)._1
        map.updated(key, (pear, n))
    }
    val notification = MatchNotification(jobId, job, chosen)

    val newJobsById = jobsById - jobId
    val newState    = copy(jobsById = newJobsById, subscriptionsById = newSubscriptionQueue)
    notification -> newState
  }

  def cancelJobs(request: CancelJobs) = {
    val cancelled = request.ids.map { id =>
      id -> jobsById.contains(id)
    }
    val newJobsById = jobsById -- request.ids
    CancelJobsResponse(cancelled.toMap) -> copy(jobsById = newJobsById)
  }

  def cancelSubscriptions(request: CancelSubscriptions) = {
    val cancelled = request.ids.map { id =>
      id -> subscriptionsById.contains(id)
    }
    val newSubscriptionsById = subscriptionsById -- request.ids
    CancelSubscriptionsResponse(cancelled.toMap) -> copy(subscriptionsById = newSubscriptionsById)
  }

  def queueState(request: QueueState): QueueStateResponse = {
    val foundJobs = jobsById.collect {
      case (_, job) if request.matchesJob(job) => job
    }
    val foundWorkers = subscriptionsById.collect {
      case (key, (sub, pending)) if request.matchesSubscription(sub.details.aboutMe) =>
        PendingSubscription(key, sub, pending)
    }

    QueueStateResponse(foundJobs.toList, foundWorkers.toList)
  }

  def subscribe(inputSubscription: WorkSubscription): (WorkSubscriptionAck, ExchangeState) = {
    val (id, subscription) = inputSubscription.key match {
      case Some(key) => (key, inputSubscription)
      case None =>
        val key             = nextSubscriptionKey()
        val newSubscription = inputSubscription.withDetails(_.withSubscriptionKey(key))

        logger.debug(s"Created new subscription [${key}] $newSubscription")
        key -> newSubscription
    }

    val newSubscriptionsById = subscriptionsById.updated(id, subscription -> 0)
    val newState             = copy(subscriptionsById = newSubscriptionsById)
    WorkSubscriptionAck(id) -> newState
  }

  def updatePending(id: SubscriptionKey, n: Int) = {
    val newSubscriptionsById = subscriptionsById.get(id).fold(subscriptionsById) {
      case (sub, before) =>
        logger.debug(s"changing pending subscriptions for [$id] from $before to $n")
        subscriptionsById.updated(id, sub -> n)
    }
    copy(subscriptionsById = newSubscriptionsById)
  }

  def isValidSubscription(id: SubscriptionKey) = {
    subscriptionsById.contains(id) || compositeSubscriptionsById.contains(id)
  }

  /**
    * Returns a new state if all the composite subscription ids are valid
    *
    * @param inputCompose the composition request
    * @return an updated state containing the subscription, if valid
    */
  def addCompositeSubscription(inputCompose: Compose): Try[(WorkSubscriptionAck, ExchangeState)] = {
    val id = inputCompose.subscription.key.getOrElse(nextSubscriptionKey())

    val compose = inputCompose.withSubscriptionId(id)

    Try {
      val newCompositeSubscriptionsById = compositeSubscriptionsById.updated(id, compose)
      val newState                      = copy(compositeSubscriptionsById = newCompositeSubscriptionsById)

      // eagerly validate we don't have any circular refs
      newState.resolvedCompositeSubscriptionsById
      WorkSubscriptionAck(id) -> newState
    }
  }

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
}

object ExchangeState {

  /** @param jobId the job id belonging to the job to match
    * @param job   the job to match
    * @return a collection of subscription keys, subscriptions and the remaining items which would match the given job
    */
  def workCandidatesForJob(jobId: JobId, job: SubmitJob, subscriptionsById: Map[SubscriptionKey, (WorkSubscription, Int)])(implicit matcher: JobPredicate): CandidateSelection = {
    subscriptionsById.collect {
      case (id, (subscription, requested)) if requested > 0 && job.matches(subscription) =>
        (id, subscription, (requested - 1).ensuring(_ >= 0))
    }.toSeq
  }

  private[exchange] def flatten(compositeSubscriptionsById: Map[SubscriptionKey, Compose]): Set[SubscriptionKey] = {
    val values                      = compositeSubscriptionsById.values.flatMap(_.subscriptions)
    val idSet: Set[SubscriptionKey] = values.toSet
    idSet -- compositeSubscriptionsById.keySet
  }

  private[exchange] def flattenSubscriptions(compositeSubscriptionsById: Map[SubscriptionKey, Compose],
                                             id: SubscriptionKey,
                                             seen: Set[SubscriptionKey] = Set.empty): Set[SubscriptionKey] = {
    compositeSubscriptionsById.get(id).map(_.subscriptions) match {
      case Some(ids) =>
        require(!seen.contains(id), s"Circular reference discovered w/ '$id'")
        val newSeen: Set[SubscriptionKey] = seen + id
        ids.foldLeft(Set.empty[SubscriptionKey]) {
          case (found, potentialCompositeId) =>
            val flattened: Set[SubscriptionKey] = flattenSubscriptions(compositeSubscriptionsById, potentialCompositeId, newSeen)
            found ++ flattened
        }
      case None =>
        // the 'id' isn't a composite ID, so just return it (assuming it is another, valid id, to be confirmed
        // by the calling function)
        Set(id)
    }
  }
}
