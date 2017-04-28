package jabroni.api
package client

import io.circe.Decoder.Result
import io.circe.export.Exported
import io.circe.{Decoder, Encoder, HCursor, Json}
import jabroni.api.exchange.{JobPredicate, WorkSubscription}
import jabroni.api.worker.WorkerDetails
import io.circe.generic.auto._
import io.circe.syntax._

import scala.language.implicitConversions

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
}

object SubmitJob {

  import io.circe.generic.auto.exportEncoder
  import io.circe.generic.auto.exportDecoder

  implicit val encoder: Encoder[SubmitJob] = exportEncoder[SubmitJob].instance
  implicit val decoder: Decoder[SubmitJob] = exportDecoder[SubmitJob].instance

  trait LowPriorityImplicits {
    implicit def asJob[T: Encoder](value: T) = new {
      def asJob(details: SubmissionDetails = SubmissionDetails()): SubmitJob = SubmitJob[T](details, value)
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

  implicit object SubmitJobResponseSupport extends ResponseSupport[SubmitJobResponse] {
    override def apply(submit: SubmitJobResponse) = submit.asJson

    override def apply(c: HCursor): Result[SubmitJobResponse] = c.as[SubmitJobResponse]
  }

}

//case class GetSubmissionResponse(id: JobId, job: Option[SubmitJob]) extends ClientResponse
//
//case class CancelSubmissionResponse(id: JobId, cancelled: Boolean) extends ClientResponse
//
//case class GetMatchedWorkersResponse(id: JobId, workers: List[WorkerDetails]) extends ClientResponse

