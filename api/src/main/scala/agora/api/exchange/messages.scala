package agora.api.exchange

import agora.api.json.JMatcher
import agora.api.worker.{HostLocation, SubscriptionKey, WorkerDetails, WorkerRedirectCoords}
import agora.api.{JobId, MatchId}
import io.circe.generic.auto._
import io.circe.{Encoder, Json}

import scala.language.implicitConversions

/**
  * A 'client' represents something which submits work to the exchange
  */
sealed trait ClientRequest

sealed trait ClientResponse

/**
  * Queries the exchange for a view of the pending jobs and subscriptions
  *
  * @param workerSubscriptionMatcher         the same as what a SubmitJob matcher would be, used to match work subscriptions
  * @param submitJobMatcher                  the same as a subscription matcher would be, used to match submitted job requests
  * @param submitJobSubmissionDetailsMatcher also as per a subscription matcher, this time matching the submissionDetails
  */
case class QueueState(workerSubscriptionMatcher: JMatcher = JMatcher.matchAll,
                      submitJobMatcher: JMatcher = JMatcher.matchAll,
                      submitJobSubmissionDetailsMatcher: JMatcher = JMatcher.matchAll)
    extends ClientRequest {
  def matchesSubscription(aboutMe: Json) = workerSubscriptionMatcher.matches(aboutMe)

  def matchesJob(job: SubmitJob) = {
    submitJobMatcher.matches(job.job) && submitJobSubmissionDetailsMatcher.matches(job.submissionDetails.aboutMe)
  }
}

object QueueState {
  implicit val encoder = exportEncoder[QueueState].instance
  implicit val decoder = exportDecoder[QueueState].instance

}

case class QueueStateResponse(jobs: List[SubmitJob], subscriptions: List[PendingSubscription]) extends ClientResponse {
  def isEmpty = jobs.isEmpty && subscriptions.isEmpty

  def description = {
    def fmtJob(job: SubmitJob) = s"${job.job.noSpaces} ;; aboutMe :${job.submissionDetails.aboutMe.noSpaces}"

    def fmtSubscription(subscription: PendingSubscription) = s"${subscription.key} w/ ${subscription.requested} requested: ${subscription.subscription.details.aboutMe.noSpaces}"

    s"""QUEUE {
       |  ${jobs.size} Jobs {
       |${jobs.map(fmtJob).mkString("\t\t", "\n\t\t", "")}
       |  }
       |  ${subscriptions.size} Subscriptions {
       |${subscriptions.map(fmtSubscription).mkString("\t\t", "\n\t\t", "")}
       |  }
       |}
       |""".stripMargin
  }
}

object QueueStateResponse {
  implicit val encoder = exportEncoder[QueueStateResponse].instance
  implicit val decoder = exportDecoder[QueueStateResponse].instance
}

case class PendingSubscription(key: SubscriptionKey, subscription: WorkSubscription, requested: Int)

/**
  * Represents anything which can be run as a job
  *
  * Json is a bit prescriptive, but that's going to cover 95% of the cases.
  * Even if we have binary data, we can base64 encode it as an option.
  *
  * If we end up doing a lot of data transfer, then we can change the job representation to be an akka Source
  *
  * @param job represents the job submission. As the job repo is heterogeneous, it could match anything really that's
  *            asking for work
  */
case class SubmitJob(submissionDetails: SubmissionDetails, job: Json) extends ClientRequest {
  def matches(work: WorkSubscription)(implicit matcher: JobPredicate) = matcher.matches(this, work)

  def withSelection(mode: SelectionMode) = copy(submissionDetails = submissionDetails.copy(selection = mode))

  def matching[T](matcher: T)(implicit ev: T => JMatcher): SubmitJob = {
    copy(submissionDetails.copy(workMatcher = ev(matcher)))
  }

  def jobId: Option[JobId] = {
    submissionDetails.valueOf[JobId]("jobId").right.toOption
  }

  def add[T: Encoder](keyValue: (String, T)): SubmitJob = withData(keyValue._2, keyValue._1)

  def withId(jobId: JobId): SubmitJob = add("jobId" -> jobId)

  def withAwaitMatch(awaitMatch: Boolean): SubmitJob = copy(submissionDetails = submissionDetails.copy(awaitMatch = awaitMatch))

  def withData[T: Encoder](data: T, name: String = null) = {
    copy(submissionDetails = submissionDetails.withData(data, name))
  }

}

object SubmitJob {
  implicit val encoder = exportEncoder[SubmitJob].instance
  implicit val decoder = exportDecoder[SubmitJob].instance

  trait LowPriorityImplicits {
    implicit def asJob[T: Encoder](value: T) = new {
      def asJob(implicit details: SubmissionDetails = SubmissionDetails()): SubmitJob = {
        require(!value.isInstanceOf[SubmitJob], s"'asJob' called on $value which is already a SubmitJob")
        SubmitJob[T](details, value)
      }
    }
  }

  def apply[T: Encoder](details: SubmissionDetails, value: T): SubmitJob = {
    val asJson = implicitly[Encoder[T]]
    SubmitJob(details, asJson(value))
  }
}

case class SubmitJobResponse(id: JobId) extends ClientResponse

object SubmitJobResponse {
  implicit val encoder = exportEncoder[SubmitJobResponse].instance
  implicit val decoder = exportDecoder[SubmitJobResponse].instance
}

case class BlockingSubmitJobResponse(matchId: MatchId, jobId: JobId, matchEpochUTC: Long, workerCoords: List[WorkerRedirectCoords], workers: List[WorkerDetails])
    extends ClientResponse {
  def firstWorkerUrl: Option[String] = workers.collectFirst {
    case w if w.url.isDefined => w.url.get
  }
}

object BlockingSubmitJobResponse {
  implicit val encoder = exportEncoder[BlockingSubmitJobResponse].instance
  implicit val decoder = exportDecoder[BlockingSubmitJobResponse].instance
}

case class CancelJobs(ids: Set[JobId]) extends ClientRequest

object CancelJobs {
  implicit val encoder = exportEncoder[CancelJobs].instance
  implicit val decoder = exportDecoder[CancelJobs].instance
}

case class CancelJobsResponse(canceledJobs: Map[JobId, Boolean]) extends ClientResponse

case class CancelSubscriptions(ids: Set[SubscriptionKey]) extends ClientRequest

case class CancelSubscriptionsResponse(canceledSubscriptions: Map[SubscriptionKey, Boolean]) extends ClientResponse

sealed trait SubscriptionRequest

sealed trait SubscriptionResponse

/**
  * The details contain info about the worker subscribing to work, such as it's location (where work should be sent to),
  * and any arbitrary json data it wants to expose (nr of CPUs, runAs user, available memory, OS, a 'topic', etc)
  *
  * Once a WorkSubscription is sent
  *
  * @param details
  * @param jobMatcher             the json matcher used against the 'job' portion of SubmitJob
  * @param submissionMatcher      the json matcher used against the additional 'details' part of SubmitJob
  * @param subscriptionReferences If non-empty, changes to the number of work items requested for this subscription will be performed on the referenced subscriptions
  */
case class WorkSubscription(details: WorkerDetails, jobMatcher: JMatcher, submissionMatcher: JMatcher, subscriptionReferences: Set[SubscriptionKey]) extends SubscriptionRequest {
  def matches(job: SubmitJob)(implicit m: JobPredicate): Boolean = m.matches(job, this)

  def key = details.subscriptionKey

  /**
    * @param matcher
    * @return a subscription with the matcher replaces
    */
  def matchingJob(matcher: JMatcher): WorkSubscription = copy(jobMatcher = matcher)

  def withReferences(references: Set[SubscriptionKey]) = copy(subscriptionReferences = references)

  def referencing(reference: SubscriptionKey, theRest: SubscriptionKey*) = withReferences(theRest.toSet + reference)

  def matchingSubmission[T](matcher: T)(implicit ev: T => JMatcher): WorkSubscription = {
    copy(submissionMatcher = ev(matcher))
  }

  def withData[T: Encoder](data: T, name: String = null) = withDetails(_.withData(data, name))

  def withPath(path: String): WorkSubscription = withDetails(_.withPath(path))

  def append[T: Encoder](name: String, data: T) = withDetails(_.append(name, data))

  def append(data: Json) = withDetails(_.append(data))

  def withSubscriptionKey(id: SubscriptionKey) = withDetails(_.withSubscriptionKey(id))

  def withDetails(f: WorkerDetails => WorkerDetails) = {
    copy(details = f(details))
  }
}

object WorkSubscription {

  def localhost(port: Int) = apply(HostLocation.localhost(port))

  /**
    * Creates a work subscription for a worker running on the given location
    * @param location - the HostLocation where this worker is running
    * @return a work subscription
    */
  def apply(location: HostLocation,
            jobMatcher: JMatcher = JMatcher.matchAll,
            submissionMatcher: JMatcher = JMatcher.matchAll,
            subscriptionReferences: Set[SubscriptionKey] = Set.empty): WorkSubscription = {
    forDetails(WorkerDetails(location), jobMatcher, submissionMatcher, subscriptionReferences)
  }

  def forDetails(details: WorkerDetails,
                 jobMatcher: JMatcher = JMatcher.matchAll,
                 submissionMatcher: JMatcher = JMatcher.matchAll,
                 subscriptionReferences: Set[SubscriptionKey] = Set.empty): WorkSubscription = {
    new WorkSubscription(details, jobMatcher, submissionMatcher, subscriptionReferences)
  }

  implicit val encoder = exportEncoder[WorkSubscription].instance
  implicit val decoder = exportDecoder[WorkSubscription].instance
}

case class WorkSubscriptionAck(id: SubscriptionKey) extends SubscriptionResponse

object WorkSubscriptionAck {
  implicit val encoder = exportEncoder[WorkSubscriptionAck].instance
  implicit val decoder = exportDecoder[WorkSubscriptionAck].instance
}

case class RequestWork(id: SubscriptionKey, itemsRequested: Int) extends SubscriptionRequest {
  require(itemsRequested > 0)

  def dec = copy(itemsRequested = itemsRequested - 1)
}

object RequestWork {

  implicit val encoder = exportEncoder[RequestWork].instance
  implicit val decoder = exportDecoder[RequestWork].instance
}

case class RequestWorkAck(id: SubscriptionKey, previousItemsPending: Int, totalItemsPending: Int) extends SubscriptionResponse {
  private[exchange] def withNewTotal(remaining: Int) = copy(totalItemsPending = remaining)

  /** @return true if a subscription previously had 0 pending subscriptions
    */
  def isUpdatedFromEmpty = previousItemsPending == 0
}

object RequestWorkAck {
  implicit val encoder = exportEncoder[RequestWorkAck].instance
  implicit val decoder = exportDecoder[RequestWorkAck].instance

  def apply(id: SubscriptionKey, requested: Int): RequestWorkAck = new RequestWorkAck(id, 0, requested)
}
