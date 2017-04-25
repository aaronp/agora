package jabroni.api
package client

import io.circe.{Encoder, Json}
import jabroni.api
import jabroni.api.exchange.JobPredicate
import jabroni.api.worker.{RequestWork, WorkerDetails}

import scala.language.implicitConversions

/**
  * A 'client' represents something which submits work to the exchange
  */
sealed trait ClientRequest

case class GetSubmission(id: JobId) extends ClientRequest

case class CancelSubmission(id: JobId) extends ClientRequest

case class GetMatchedWorkers(id: JobId, blockUntilMatched: Boolean) extends ClientRequest

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
  def matches(work: RequestWork)(implicit m: JobPredicate) = m.matches(this, work)

  def select(offers: Stream[(api.WorkRequestId, RequestWork)]) = submissionDetails.selection.select(offers)

  def json = {
    import io.circe.generic.auto._
    import io.circe.syntax._
    this.asJson
  }
}

object SubmitJob {

  trait LowPriorityImplicits {
    implicit def asJob[T: Encoder](value: T) = new {
      def asJob(details: SubmissionDetails = SubmissionDetails.default()): SubmitJob = SubmitJob[T](details, value)
    }
  }

  def apply[T: Encoder](details: SubmissionDetails, value: T): SubmitJob = {
    val asJson = implicitly[Encoder[T]]
    SubmitJob(details, asJson(value))
  }
}

sealed trait ClientResponse

case class SubmitJobResponse(id: JobId) extends ClientResponse

case class GetSubmissionResponse(id: JobId, job: Option[SubmitJob]) extends ClientResponse

case class CancelSubmissionResponse(id: JobId, cancelled: Boolean) extends ClientResponse

case class GetMatchedWorkersResponse(id: JobId, workers: List[WorkerDetails]) extends ClientResponse

