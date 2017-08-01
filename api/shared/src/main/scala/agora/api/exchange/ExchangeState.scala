package agora.api.exchange

import agora.api.worker._
import agora.api.{JobId, nextJobId, nextSubscriptionKey}
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

case class ExchangeState(subscriptionsById: Map[SubscriptionKey, (WorkSubscription, Int)] = Map[SubscriptionKey, (WorkSubscription, Int)](),
                         compositeSubscriptionsById: Map[SubscriptionKey, Compose] = Map[SubscriptionKey, Compose](),
                         jobsById: Map[JobId, SubmitJob] = Map[JobId, SubmitJob]())
    extends StrictLogging {

  import ExchangeState._

  // validate subscription
  {
    val intersection = compositeSubscriptionsById.keySet intersect subscriptionsById.keySet

    require(
      intersection.isEmpty,
      s"$intersection is both a composite (${compositeSubscriptionsById.keySet}) and a normal subscription (${subscriptionsById.keySet})"
    )
  }

  def updateSubscription(id: SubscriptionKey, details: WorkerDetails) = {
    subscriptionsById.get(id) match {
      case Some((subscription, n)) =>
        val before          = Option(subscription.details)
        val newSubscription = subscription.append(details.aboutMe)
        val newState        = copy(subscriptionsById = subscriptionsById.updated(id, (newSubscription, n)))
        val after           = Option(newSubscription.details)
        UpdateWorkSubscriptionAck(id, before, after) -> newState
      case None =>
        UpdateWorkSubscriptionAck(id, None, None) -> this
    }
  }

  private def compositePending(key: SubscriptionKey): Option[Int] = {
    resolvedCompositeSubscriptionsById.get(key).map {
      case (_, keys) =>
        val pendingCounts = keys.flatMap(subscriptionsById.get).map(_._2)
        if (pendingCounts.isEmpty) 0 else pendingCounts.min
    }
  }

  /** @param key the subscription key
    * @return the number of work items pending for the given subscription, or 0 if the subscription is unknown
    */
  private[exchange] def pending(key: SubscriptionKey): Int = {
    subscriptionsById.get(key).map(_._2).orElse(compositePending(key)).getOrElse(0)
  }

  lazy val subscriptionKeys = subscriptionsById.keySet ++ compositeSubscriptionsById.keySet
  private lazy val resolvedCompositeSubscriptionsById: Map[SubscriptionKey, (Compose, Set[SubscriptionKey])] = {
    compositeSubscriptionsById.map {
      case (compositeId, compose) =>
        val ids        = flattenSubscriptions(compositeSubscriptionsById, compositeId)
        val invalidIds = ids.filterNot(subscriptionsById.contains)
        require(invalidIds.isEmpty, s"composite subscription '$compositeId' refers to invalid subscription id: $invalidIds")
        compositeId -> (compose, ids)
    }
  }

  def findCompositeCandidate(job: SubmitJob, candidates: CandidateSelection)(implicit matcher: JobPredicate): Option[(SubscriptionKey, Compose, CandidateSelection)] = {
    val candidateIds = candidates.map(_.subscriptionKey)
    val compositeMatchOpt = resolvedCompositeSubscriptionsById.collectFirst {
      case (composeId, (compose, ids)) if job.matches(compose.subscription) && ids.forall(candidateIds.contains) =>
        logger.debug(s"Composite subscription '$composeId' matches")
        val compositeCandidates = candidates.filter {
          case Candidate(id, _, _) => ids.contains(id)
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
            val (_, newState)         = oldState.createMatch(jobId, job, filtered)
            val minRemaining          = filtered.map(_.remaining).min
            val candidateSelection    = List(Candidate(compositeId, composite.subscription, minRemaining))
            val candidateNotification = MatchNotification(jobId, job, candidateSelection)
            (candidateNotification :: matches) -> newState
          case None =>
            val chosen: Seq[Candidate] = job.submissionDetails.selection.select(candidates)

            if (chosen.isEmpty) {
              accumulator
            } else {
              val (thisMatch, newState) = oldState.createMatch(jobId, job, chosen)
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
      case (map, Candidate(key, _, n)) =>
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

  private def containsSubscription(id: SubscriptionKey) = compositeSubscriptionsById.contains(id) || subscriptionsById.contains(id)

  /** cancels the subscription IDs.
    * If the id is a composite ID then that composite subscription alone is cancelled, not the constituent subscriptions.
    *
    * Any composite subscriptions referring to the cancelled subscription are also cancelled, however.
    *
    * @param ids the subscription IDs to cancel
    * @return a cancelled response and updated state
    */
  def cancelSubscriptions(ids: Set[SubscriptionKey]): (CancelSubscriptionsResponse, ExchangeState) = {

    val compositeIdsToCancel = resolvedCompositeSubscriptionsById.collect {
      case (compId, (_, subscriptionIds)) if subscriptionIds.exists(ids.contains) => compId
    }

    val newSubscriptionsById     = subscriptionsById -- ids
    val newCompositeSubscription = (compositeSubscriptionsById -- ids) -- compositeIdsToCancel
    val newState                 = copy(subscriptionsById = newSubscriptionsById, compositeSubscriptionsById = newCompositeSubscription)

    val cancelled = (compositeIdsToCancel ++ ids).map { id =>
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

    val requested            = subscriptionsById.get(id).map(_._2).getOrElse(0)
    val newSubscriptionsById = subscriptionsById.updated(id, subscription -> requested)
    val newState             = copy(subscriptionsById = newSubscriptionsById)
    WorkSubscriptionAck(id) -> newState
  }

  private def updatePending(id: SubscriptionKey, n: Int): ExchangeState = {
    subscriptionsById.get(id).fold(this) {
      case (sub, before) =>
        logger.debug(s"changing pending subscriptions for [$id] from $before to $n")
        val newSubscriptionsById = subscriptionsById.updated(id, sub -> n)
        copy(subscriptionsById = newSubscriptionsById)
    }
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

  /**
    * A request for 'n' work items from a composite subscription results in n work items requested for the underlying
    * subscriptions
    *
    * @param id the composite subscription id
    * @param n  the number of work items to request
    * @return failure if the id isn't a composite subscription, success with an ack/ updated state when it is
    */
  private def requestComposite(id: SubscriptionKey, n: Int): Try[(RequestWorkAck, ExchangeState)] = {
    resolvedCompositeSubscriptionsById.get(id) match {
      case None =>
        Failure(new Exception(s"subscription '$id' doesn't exist. Known ${subscriptionsById.size} subscriptions are: ${subscriptionKeys.mkString(",")}"))
      case Some((_, ids)) =>
        val (newSubscriptionsById, ack) = ids.foldLeft((subscriptionsById, RequestWorkAck(Map.empty))) {
          case ((map, RequestWorkAck(ack)), id) =>
            val (sub, before) = subscriptionsById(id)

            val newPending = before + n
            val newMap     = map.updated(id, sub -> newPending)
            newMap -> RequestWorkAck(ack.updated(id, RequestWorkUpdate(before, newPending)))
        }

        val newState = copy(subscriptionsById = newSubscriptionsById)
        Success(ack -> newState)
    }
  }

  def request(id: SubscriptionKey, n: Int): Try[(RequestWorkAck, ExchangeState)] = {
    require(n >= 0)
    subscriptionsById.get(id) match {
      case None => requestComposite(id, n)
      case Some((_, before)) =>
        val newState = updatePending(id, before + n)
        Success(RequestWorkAck(Map(id -> RequestWorkUpdate(before, newState.pending(id)))) -> newState)
    }
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
        Candidate(id, subscription, (requested - 1).ensuring(_ >= 0))
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
