package jabroni.api

import java.net.URI

import io.circe.{Encoder, Json}
import jabroni.api.json.JsonMatcher

import scala.util.Properties
import scala.language.implicitConversions

sealed trait ClientRequest

sealed trait ClientResponse

case class SubmitJobResponse(id: JobId) extends ClientResponse

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
case class SubmitJob(submissionDetails: SubmissionDetails, job: Json) extends ClientRequest

object SubmitJob {

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

sealed trait WorkerRequest

sealed trait WorkerResponse

case class WorkerDetails(aboutMe: Json, location: URI, runUser: User)

case class RequestWork(worker: WorkerDetails, workMatcher: JsonMatcher, itemsRequested: Int) extends WorkerRequest

case class RequestWorkResponse(id: WorkRequestId) extends WorkerResponse

/**
  * Contains instructions/information specific to the job scheduling/matching
  *
  * @param matcher
  */
case class SubmissionDetails(submittedBy: User = Properties.userName,
                             matcher: JsonMatcher = JsonMatcher.matchAll)
