package jabroni.api.exchange

import io.circe.generic.auto._
import io.circe.{Encoder, Json}
import jabroni.api.`match`.MatchDetails
import jabroni.api.json.{JMatcher, JPath}
import jabroni.api.worker.{SubscriptionKey, WorkerDetails}
import jabroni.api.{JobId, MatchId, nextJobId}

import scala.language.implicitConversions


sealed trait ObserverRequest

sealed trait ObserverResponse

case class QueuedJobs(subscriptionMatcher: JMatcher, jobMatcher: JMatcher) extends ObserverRequest {
  def this() = this(JMatcher.matchAll, JMatcher.matchAll)

  def matches(job: SubmitJob) = {
    jobMatcher.matches(job.job) && subscriptionMatcher.matches(job.submissionDetails.aboutMe)
  }
}

object QueuedJobs {
  implicit val encoder = exportEncoder[QueuedJobs].instance
  implicit val decoder = exportDecoder[QueuedJobs].instance
}

case class QueuedJobsResponse(jobs: List[SubmitJob]) extends ObserverResponse

object QueuedJobsResponse {
  implicit val encoder = exportEncoder[QueuedJobsResponse].instance
  implicit val decoder = exportDecoder[QueuedJobsResponse].instance
}

case class ListSubscriptions(subscriptionCriteria: JMatcher) extends ObserverRequest {
  def this() = this(JMatcher.matchAll)
}

object ListSubscriptions {
  implicit val encoder = exportEncoder[ListSubscriptions].instance
  implicit val decoder = exportDecoder[ListSubscriptions].instance
}

case class PendingSubscription(key: SubscriptionKey, subscription: WorkSubscription, requested: Int)

case class ListSubscriptionsResponse(subscriptions: List[PendingSubscription]) extends ObserverResponse

object ListSubscriptionsResponse {
  implicit val encoder = exportEncoder[ListSubscriptionsResponse].instance
  implicit val decoder = exportDecoder[ListSubscriptionsResponse].instance
}

/**
  * A 'client' represents something which submits work to the exchange
  */
sealed trait ClientRequest

//
//case class GetSubmission(id: JobId) extends ClientRequest
//
//case class CancelSubmission(id: JobId) extends ClientRequest
//
//case class GetMatchedWorkers(id: JobId, blockUntilMatched: Boolean) extends ClientRequest

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
  def matches(work: WorkSubscription)(implicit m: JobPredicate) = m.matches(this, work)

  def jobId: Option[JobId] = {
    submissionDetails.valueOf[JobId]("jobId").right.toOption
  }

  def +[T: Encoder](keyValue: (String, T)): SubmitJob = add(keyValue)

  def add[T: Encoder](keyValue: (String, T)): SubmitJob = withData(keyValue._2, keyValue._1)

  def withId(jobId: JobId): SubmitJob = add("jobId" -> jobId)

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

sealed trait ClientResponse

case class SubmitJobResponse(id: JobId) extends ClientResponse

object SubmitJobResponse {
  implicit val encoder = exportEncoder[SubmitJobResponse].instance
  implicit val decoder = exportDecoder[SubmitJobResponse].instance
}

case class BlockingSubmitJobResponse(matchId: MatchId, jobId: JobId, matchEpochUTC: Long, workers: List[WorkerDetails]) extends ClientResponse {
  def firstWorkerUrl = workers.collectFirst {
    case w if w.url.isDefined => w.url.get
  }
}

object BlockingSubmitJobResponse {
  implicit val encoder = exportEncoder[BlockingSubmitJobResponse].instance
  implicit val decoder = exportDecoder[BlockingSubmitJobResponse].instance
}

//case class GetSubmissionResponse(id: JobId, job: Option[SubmitJob]) extends ClientResponse
//
//case class CancelSubmissionResponse(id: JobId, cancelled: Boolean) extends ClientResponse
//
//case class GetMatchedWorkersResponse(id: JobId, workers: List[WorkerDetails]) extends ClientResponse


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
case class WorkSubscription(details: WorkerDetails = WorkerDetails(),
                            jobMatcher: JMatcher = JMatcher.matchAll,
                            submissionMatcher: JMatcher = JMatcher.matchAll) extends SubscriptionRequest {
  def matches(job: SubmitJob)(implicit m: JobPredicate): Boolean = m.matches(job, this)

  def append(json: Json) = {
    copy(details = details.append(json))
  }

  def and(matcher: JMatcher) = matching(jobMatcher.and(matcher))

  def or(matcher: JMatcher) = matching(jobMatcher.or(matcher))

  /**
    * @param matcher
    * @return a subscription with the matcher replaces
    */
  def matching(matcher: JMatcher) = copy(jobMatcher = matcher)

  def withData[T: Encoder](data: T, name: String = null) = {
    copy(details = details.withData(data, name))
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

case class RequestWork(id: SubscriptionKey,
                       itemsRequested: Int) extends SubscriptionRequest {
  require(itemsRequested > 0)

  def dec = copy(itemsRequested = itemsRequested - 1)
}

object RequestWork {

  implicit val encoder = exportEncoder[RequestWork].instance
  implicit val decoder = exportDecoder[RequestWork].instance
}

case class RequestWorkAck(id: SubscriptionKey, totalItemsPending: Int) extends SubscriptionResponse

object RequestWorkAck {
  implicit val encoder = exportEncoder[RequestWorkAck].instance
  implicit val decoder = exportDecoder[RequestWorkAck].instance
}
