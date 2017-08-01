package agora.api.exchange

import agora.api.json.JMatcher
import agora.api.worker.{SubscriptionKey, WorkerDetails, WorkerRedirectCoords}
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

/** Composes the subscriptions, responded with an ack
  *
  * @param subscriptions
  */
case class Compose(subscription: WorkSubscription, subscriptions: Set[SubscriptionKey]) extends SubscriptionRequest {
  def withSubscriptionId(id: SubscriptionKey) = {
    copy(subscription = subscription.withSubscriptionKey(id))
  }

  require(subscriptions.nonEmpty, "invalid 'Compose' - no subscription keys specified")
}

object Compose {
  def apply(subscription: WorkSubscription, first: SubscriptionKey, theRest: SubscriptionKey*) = {
    new Compose(subscription, theRest.toSet + first)
  }
}

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

  def matching(matcher: JMatcher) = copy(submissionDetails.copy(workMatcher = matcher))

  def jobId: Option[JobId] = {
    submissionDetails.valueOf[JobId]("jobId").right.toOption
  }

  def +[T: Encoder](keyValue: (String, T)): SubmitJob = add(keyValue)

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
      def asJob(implicit details: SubmissionDetails = SubmissionDetails()): SubmitJob = SubmitJob[T](details, value)
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
  * @param jobMatcher        the json matcher used against the 'job' portion of SubmitJob
  * @param submissionMatcher the json matcher used against the additional 'details' part of SubmitJob
  */
case class WorkSubscription(details: WorkerDetails = WorkerDetails(), jobMatcher: JMatcher = JMatcher.matchAll, submissionMatcher: JMatcher = JMatcher.matchAll)
    extends SubscriptionRequest {
  def matches(job: SubmitJob)(implicit m: JobPredicate): Boolean = m.matches(job, this)

  def key = details.subscriptionKey

  /**
    * @param matcher
    * @return a subscription with the matcher replaces
    */
  def matchingJob(matcher: JMatcher): WorkSubscription = copy(jobMatcher = matcher)

  def matchingSubmission(matcher: JMatcher) = copy(submissionMatcher = matcher)

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
  implicit val encoder = exportEncoder[WorkSubscription].instance
  implicit val decoder = exportDecoder[WorkSubscription].instance
}

case class WorkSubscriptionAck(id: SubscriptionKey) extends SubscriptionResponse

object WorkSubscriptionAck {
  implicit val encoder = exportEncoder[WorkSubscriptionAck].instance
  implicit val decoder = exportDecoder[WorkSubscriptionAck].instance
}


case class UpdateWorkSubscription(id : SubscriptionKey, details: WorkerDetails) extends SubscriptionRequest
case class UpdateWorkSubscriptionAck(id : SubscriptionKey, before: Option[WorkerDetails], after: Option[WorkerDetails]) extends SubscriptionResponse


case class RequestWork(id: SubscriptionKey, itemsRequested: Int) extends SubscriptionRequest {
  require(itemsRequested > 0)

  def dec = copy(itemsRequested = itemsRequested - 1)
}

object RequestWork {

  implicit val encoder = exportEncoder[RequestWork].instance
  implicit val decoder = exportDecoder[RequestWork].instance
}

case class RequestWorkUpdate(previousItemsPending: Int, totalItemsPending: Int) extends SubscriptionResponse

case class RequestWorkAck(updated: Map[SubscriptionKey, RequestWorkUpdate]) extends SubscriptionResponse {
  private[exchange] def withNewTotal(id: SubscriptionKey, remaining: Int) = {
    val newUpdate = updated(id).copy(totalItemsPending = remaining)
    copy(updated = updated.updated(id, newUpdate))
  }


  /** @return true if a subscription previously had 0 pending subscriptions
    */
  def isUpdatedFromEmpty = {
    updated.values.exists {
      case RequestWorkUpdate(before, _) => before == 0
    }
  }
}

object RequestWorkAck {
  implicit val encoder = exportEncoder[RequestWorkAck].instance
  implicit val decoder = exportDecoder[RequestWorkAck].instance

  def apply(id : SubscriptionKey, requested : Int) : RequestWorkAck = new RequestWorkAck(Map(id -> RequestWorkUpdate(0, requested)))
}
