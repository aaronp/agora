package agora.api.exchange.instances

import agora.api.exchange._
import agora.api.exchange.bucket.{BucketMap, BucketPathKey, WorkerMatchBucket}
import agora.api.exchange.observer.{ExchangeObserver, OnMatch}
import agora.json.JsonDelta
import agora.time.Timestamp
import agora.api.worker._
import agora.api.{JobId, nextJobId, nextSubscriptionKey}
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

/**
  * An immutable view of the exchange state
  *
  */
case class ExchangeState(observer: ExchangeObserver = ExchangeObserver(),
                         subscriptionsById: Map[SubscriptionKey, (WorkSubscription, Requested)] = Map[SubscriptionKey, (WorkSubscription, Requested)](),
                         bucketsByKey: Map[BucketPathKey, BucketMap] = Map.empty,
                         jobsById: Map[JobId, SubmitJob] = Map[JobId, SubmitJob]())
    extends StrictLogging { self =>

  /** @param key the subscription key
    * @return the number of work items pending for the given subscription, or 0 if the subscription is unknown
    */
  private[exchange] def pending(key: SubscriptionKey): Int = {
    val remainingOpt = subscriptionsById.get(key).map(_._2).map { remaining =>
      remaining.remaining(this)
    }
    remainingOpt.getOrElse(0)
  }

  /** @return true if there are any subscriptions requesting work
    */
  def hasPendingSubscriptions = subscriptionsById.exists {
    case (_, (_, requested)) => requested.remaining(this) > 0
  }

  def nonEmptySubscriptions: Map[SubscriptionKey, (WorkSubscription, Requested)] =
    subscriptionsById.filter {
      case (_, (_, requested)) => requested.remaining(this) > 0
    }

  /** @param id the subscription id
    * @return a copy of the state which only contains the given subscription
    */
  def withSubscription(id: SubscriptionKey): Option[ExchangeState] = {
    subscriptionsById.get(id).map { valuePair =>
      copy(subscriptionsById = Map(id -> valuePair))
    }
  }

  def withJob(id: JobId) = {
    jobsById.get(id).map { onlyJob =>
      copy(jobsById = Map(id -> onlyJob))
    }
  }

  /**
    * @return a new state with the 'orElse' version of the submitted jobs and any non-empty work subscriptions
    */
  def orElseState: Option[ExchangeState] = {
    for {
      jobs <- orElseJobs
      newSubscriptions = nonEmptySubscriptions
      if newSubscriptions.nonEmpty
    } yield {
      copy(subscriptionsById = newSubscriptions, jobsById = jobs)
    }
  }

  private def orElseJobs: Option[Map[JobId, SubmitJob]] = {
    val jobList: Map[JobId, SubmitJob] = jobsById.flatMap {
      case (id, job) => job.orElseSubmission.map(id ->)
    }
    if (jobList.isEmpty) {
      None
    } else {
      Option(jobList)
    }
  }

  private[exchange] def updatePending(key: SubscriptionKey, delta: Int) = updateSubscriptionOnMatch(key, delta)

  private[exchange] def calculatePending(key: SubscriptionKey, delta: Int): Int = {
    val state = calculatePendingRecursive(key, delta)
    state.subscriptionsById.get(key) match {
      case Some((_, FixedRequested(n))) => n
      case _                            => 0
    }
  }

  private[exchange] def calculatePendingRecursive(key: SubscriptionKey, delta: Int, seenCheck: Set[SubscriptionKey] = Set.empty): ExchangeState = {
    val newStateOpt = subscriptionsById.get(key).map {
      case (sub, FixedRequested(n)) =>
        val newRequested = FixedRequested((n + delta).max(0))

        copy(subscriptionsById = subscriptionsById.updated(key, (sub, newRequested)))
      case (sub, LinkedRequested(ids)) =>
        val seen = seenCheck + key
        ids.foldLeft(this) {
          case (_, id) if seen.contains(id) => sys.error(s"Circular reference detected with linked subscription from $key -> $id, seen: $seen")
          case (state, id)                  => state.calculatePendingRecursive(id, delta, seen + id)
        }
    }

    newStateOpt.getOrElse(this)
  }

  /**
    * Alter the requested count by 'delta' for this subscription key (including referenced subscriptions)
    *
    * @param key
    * @param delta
    * @param updates
    * @param seenCheck
    * @return
    */
  private[exchange] def updateSubscriptionOnMatch(key: SubscriptionKey,
                                                  delta: Int,
                                                  updates: Vector[OnMatchUpdateAction] = Vector.empty,
                                                  seenCheck: Set[SubscriptionKey] = Set.empty): ExchangeState = {
    val newStateOpt = updateSubscriptionOnMatchOpt(key, delta, updates, seenCheck)
    newStateOpt.getOrElse(this)
  }

  private[exchange] def updateSubscriptionOnMatchOpt(key: SubscriptionKey,
                                                     delta: Int,
                                                     updates: Vector[OnMatchUpdateAction] = Vector.empty,
                                                     seenCheck: Set[SubscriptionKey] = Set.empty) = {
    val newStateOpt = subscriptionsById.get(key).map {
      case (oldSub, FixedRequested(n)) =>
        val newRequested = FixedRequested((n + delta).max(0))

        observer.onSubscriptionRequestCountChanged(key, n, newRequested.n)

        val newSub = updates.foldLeft(oldSub) {
          case (workerSubscription, action) => action.update(workerSubscription)
        }
        copy(subscriptionsById = subscriptionsById.updated(key, (newSub, newRequested)))
      case (_, LinkedRequested(ids)) =>
        val seen = seenCheck + key
        ids.foldLeft(this) {
          case (_, id) if seen.contains(id) => sys.error(s"Circular reference detected with linked subscription from $key -> $id, seen: $seen")
          case (state, id)                  => state.updateSubscriptionOnMatch(id, delta, updates, seen + id)
        }
    }

    newStateOpt
  }

  /**
    * Creates matches based on the given predicate.
    *
    * Composite matches are always considered first, as by definition if an entire composite match
    * matches then all of its constituent parts would as well.
    *
    */
  def matches(matchTime: Timestamp = agora.time.now())(implicit matcher: JobPredicate): (List[OnMatch], ExchangeState) = {

    logger.trace(s"Checking for matches between ${jobsById.size} jobs and ${subscriptionsById.size} subscriptions")

    jobsById.foldLeft(List[OnMatch]() -> this) {
      case (accumulator @ (matches, oldState), (jobId, job)) =>
        val candidates: Seq[Candidate] = oldState.workCandidatesForJob(jobId, job)
        val chosen: Seq[Candidate]     = job.submissionDetails.selection.select(candidates)

        if (chosen.isEmpty) {
          accumulator
        } else {
          val (thisMatch, newState) = oldState.createMatch(jobId, job, chosen, matchTime)
          (thisMatch :: matches, newState)
        }
    }
  }

  /**
    * Our subscriptionsById map keeps a
    *
    * Map[SubscriptionKey, (WorkSubscription, Requested)]
    *
    * the 'Requsted' is a means to track how many work items a given subscription is requesting. It's not a simple
    * Integer value, as we support the concept of subscription references (e.g. several subscriptions which can
    * take from the same subscriptionId). This extractor serves as a means of resolving a 'Reequsted' to an
    * integer value so that it can be used in a collect w/o having to re-resolve the 'Requested' to an integer
    * multiple times for the same state.
    */
  private object ResolvedRequestedExtractor {
    def unapply(subscriptionPair: (WorkSubscription, Requested)): Option[(WorkSubscription, Requested, Int)] = {
      val (subscription, requested) = subscriptionPair
      val remaining                 = requested.remaining(self)
      if (remaining > 0) {
        Some(subscription, requested, remaining)
      } else {
        None
      }
    }
  }

  private def filterForBucket(jobBucket: WorkerMatchBucket): Option[Set[SubscriptionKey]] = {
    if (jobBucket.isEmpty) {
      // no bucket - consider all subscriptions
      None
    } else {
      bucketsByKey.get(jobBucket.keys).flatMap { existingBucket: BucketMap =>
        existingBucket.workSubscriptionKeysForWorkerMatchBucket(jobBucket.valueKeys)
      }
    }
  }

  /** @param jobId the job id belonging to the job to match
    * @param job   the job to match
    * @return a collection of subscription keys, subscriptions and the remaining items which would match the given job
    */
  private def workCandidatesForJob(jobId: JobId, job: SubmitJob)(implicit matcher: JobPredicate): CandidateSelection = {

    val validSubscriptions: Map[SubscriptionKey, (WorkSubscription, Requested)] = {

      // if the job specifies a bucket, then limit our subscriptions to those workers in the bucket
      val jobBucket: WorkerMatchBucket = job.workerBucket

      //
      // try and reduce the subscriptions based on buckets
      //
      val filter: Option[Set[SubscriptionKey]] = filterForBucket(jobBucket)

      logger.debug(s"worker bucket for ${jobBucket.keys} resolved to ${filter}")
      filter.fold(subscriptionsById) { idsInBucket =>
        val pairs = idsInBucket.flatMap { key =>
          subscriptionsById.get(key).map(value => key -> value)
        }
        pairs.toMap
      }
    }

    validSubscriptions.collect {
      case (id, ResolvedRequestedExtractor(subscription, requested, resolvedRequested)) if job.matches(subscription, resolvedRequested) =>
        val remaining = calculatePending(id, -1)
        Candidate(id, subscription, remaining)
    }.toSeq
  }

  private def createMatch(jobId: JobId, job: SubmitJob, chosen: CandidateSelection, matchTime: Timestamp): (OnMatch, ExchangeState) = {

    val updates = job.submissionDetails.workMatcher.onMatchUpdate
    updateStateFromMatch(OnMatch(matchTime, jobId, job, chosen), updates)
  }

  /** @return a new state w/ the match removed */
  def updateStateFromMatch(notification: OnMatch, updates: Vector[OnMatchUpdateAction]) = {
    val newJobsById = jobsById - notification.matchedJobId

    val (updatedState, updatedSelection) = notification.selection.foldLeft(copy(jobsById = newJobsById) -> List[Candidate]()) {
      case ((state, selectionList), candidate @ Candidate(key, _, _)) =>
        state.updateSubscriptionOnMatchOpt(key, -1, updates) match {
          case Some(newState) =>
            newState.subscriptionsById.get(key) match {
              case Some((updatedSubscription, _)) =>
                val newSelection = candidate.copy(subscription = updatedSubscription) :: selectionList
                newState -> newSelection
              case None => newState -> (candidate :: selectionList)
            }

          case None => state -> (candidate :: selectionList)
        }
    }

    notification.copy(selection = updatedSelection) -> updatedState
  }

  def cancelJobs(request: CancelJobs): (CancelJobsResponse, ExchangeState) = {
    val cancelled = request.ids.map { id =>
      val ok = jobsById.contains(id)
      id -> ok
    }
    val newJobsById = jobsById -- request.ids
    val resp        = CancelJobsResponse(cancelled.toMap)

    val cancelledIds = {
      val all = resp.cancelledJobs.collect {
        case (id, true) => id
      }
      all.toSet
    }
    if (cancelledIds.nonEmpty) {
      observer.onJobsCancelled(cancelledIds)
    }

    resp -> copy(jobsById = newJobsById)
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

    val cancelledMap = ids.map { id =>
      val usedToContain = containsSubscription(id)
      if (usedToContain) {
        require(!newState.containsSubscription(id), s"$id wasn't actually cancelled")
      }
      id -> usedToContain
    }

    val resp = CancelSubscriptionsResponse(cancelledMap.toMap)

    val cancelled = {
      val all = resp.cancelledSubscriptions.collect {
        case (id, true) => id
      }
      all.toSet
    }
    if (cancelled.nonEmpty) {
      observer.onSubscriptionsCancelled(cancelled)
    }

    resp -> newState
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

    /**
      * Either generate a subscription id or use the one already provided on the WorkSubscription
      */
    val (id, subscription) = inputSubscription.key match {
      case Some(key) => (key, inputSubscription)
      case None =>
        val key             = nextSubscriptionKey()
        val newSubscription = inputSubscription.withSubscriptionKey(key)

        logger.debug(s"Created new subscription [${key}] $newSubscription")
        key -> newSubscription
    }

    // create the new subscription. If there's an existing one, then treat this as an append
    val newState: ExchangeState = subscriptionsById.get(id).map(_._2) match {
      case Some(_) =>
        val update                            = UpdateSubscription(id, delta = JsonDelta(append = subscription.details.aboutMe))
        val updatedOpt: Option[ExchangeState] = updateSubscription(update)
        updatedOpt.getOrElse(this)
      case None =>
        val requested            = Requested(subscription.subscriptionReferences)
        val newSubscriptionsById = subscriptionsById.updated(id, subscription -> requested)

        val newBuckets = {
          bucketsByKey.mapValues { bucket =>
            bucket.update(id, subscription.details.aboutMe)
          }
        }

        observer.onSubscriptionCreated(id, subscription, requested.remaining(this))

        copy(subscriptionsById = newSubscriptionsById, bucketsByKey = newBuckets)
    }
    WorkSubscriptionAck(id) -> newState
  }

  /**
    * Submits the given job to the state
    *
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
    logger.debug(s"submitting job [$id] $job")

    val newBuckets = if (inputJob.workerBucket.isEmpty) {
      bucketsByKey
    } else {
      if (bucketsByKey.isEmpty || !bucketsByKey.contains(inputJob.workerBucket.keys)) {
        // this is a new bucket - group the work subscriptions
        bucketsByKey.updated(inputJob.workerBucket.keys, BucketMap(inputJob.workerBucket.keys, subscriptionsById))
      } else {
        bucketsByKey
      }
    }

    // let people know we've got a job ... we may subsequently let people know it's matched summat too
    observer.onJobSubmitted(job)

    val newJobsById = jobsById.updated(id, job)

    SubmitJobResponse(id) -> copy(jobsById = newJobsById, bucketsByKey = newBuckets)
  }

  def request(id: SubscriptionKey, n: Int): Try[(RequestWorkAck, ExchangeState)] = {
    subscriptionsById.get(id) match {
      case None =>
        Failure(new Exception(s"subscription '$id' doesn't exist. Known ${subscriptionsById.size} subscriptions are: ${subscriptionsById.keySet
          .mkString(",")}"))
      case Some((_, before)) =>
        val newState = updatePending(id, n)

        Success(RequestWorkAck(id, before.remaining(this), newState.pending(id)) -> newState)
    }
  }

  /**
    * update the subscription referenced by the given id
    *
    * @param msg the update to perform
    * @return an option of an updated state, should the subscription exist, update condition return true, and delta have effect
    */
  def updateSubscription(msg: UpdateSubscription): Option[ExchangeState] = {

    subscriptionsById.get(msg.id).flatMap {
      case (subscription, n) =>
        if (msg.condition.matches(subscription.details.aboutMe)) {
          val updated: Option[WorkSubscription] = subscription.update(msg.delta)

          //
          // our subscription has changed - update all the buckets
          //
          updated.map { newSubscription =>
            val newBuckets = {
              bucketsByKey.mapValues { bucket =>
                bucket.update(msg.id, newSubscription.details.aboutMe)
              }
            }

            observer.onSubscriptionUpdated(msg.id, newSubscription, n.remaining(this), msg.delta)
            copy(subscriptionsById = subscriptionsById.updated(msg.id, (newSubscription, n)), bucketsByKey = newBuckets)
          }
        } else {
          require(bucketsByKey.values.forall(!_.containsSubscription(msg.id)), "we have buckets referencing unknown subscriptions")
          None
        }
    }
  }

}
