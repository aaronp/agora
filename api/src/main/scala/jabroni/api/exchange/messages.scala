package jabroni.api.exchange

import io.circe.Decoder.Result
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, HCursor, Json}
import jabroni.api.json.JMatcher
import jabroni.api.worker.{SubscriptionKey, WorkerDetails}
import jabroni.api.{JobId, RequestSupport, ResponseSupport}


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

//case class GetSubmissionResponse(id: JobId, job: Option[SubmitJob]) extends ClientResponse
//
//case class CancelSubmissionResponse(id: JobId, cancelled: Boolean) extends ClientResponse
//
//case class GetMatchedWorkersResponse(id: JobId, workers: List[WorkerDetails]) extends ClientResponse




sealed trait SubscriptionRequest
sealed trait SubscriptionResponse

case class WorkSubscription(details: WorkerDetails,
                            workMatcher: JMatcher)(val onNext: (SubmitJob, Int) => Unit) extends SubscriptionRequest {
  def matches(job: SubmitJob)(implicit m: JobPredicate): Boolean = m.matches(job, this)

  def withData[T: Encoder](data: T, name: String = null) = {
    copy(details = details.withData(data, name))(onNext)
  }
}

object WorkSubscription {

  def apply(onNext: (SubmitJob, Int) => Unit)(implicit
                                              details: WorkerDetails = WorkerDetails(),
                                              workMatcher: JMatcher = JMatcher.matchAll): WorkSubscription = {
    WorkSubscription(details, workMatcher)(onNext)
  }

  implicit object Support extends RequestSupport[WorkSubscription] {
    override def apply(submit: WorkSubscription) = submit.asJson

    override def apply(c: HCursor): Result[WorkSubscription] = c.as[WorkSubscription]
  }
}

case class WorkSubscriptionAck(id: SubscriptionKey) extends SubscriptionResponse

object WorkSubscriptionAck {

  implicit object Support extends ResponseSupport[WorkSubscriptionAck] {
    override def apply(submit: WorkSubscriptionAck) = submit.asJson

    override def apply(c: HCursor): Result[WorkSubscriptionAck] = c.as[WorkSubscriptionAck]
  }
}

case class RequestWork(id: SubscriptionKey,
                       itemsRequested: Int) extends SubscriptionRequest {
  require(itemsRequested > 0)

  def dec = copy(itemsRequested = itemsRequested - 1)
}

object RequestWork {

  implicit object Support extends RequestSupport[RequestWork] {
    override def apply(submit: RequestWork) = submit.asJson
    override def apply(c: HCursor): Result[RequestWork] = c.as[RequestWork]
  }

}

case class RequestWorkAck(id: SubscriptionKey, totalItemsPending: Int) extends SubscriptionResponse

object RequestWorkAck {

  implicit object Support extends ResponseSupport[RequestWorkAck] {
    override def apply(submit: RequestWorkAck) = submit.asJson

    override def apply(c: HCursor): Result[RequestWorkAck] = c.as[RequestWorkAck]
  }

}
