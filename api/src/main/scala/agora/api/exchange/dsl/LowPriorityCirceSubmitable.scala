package agora.api.exchange.dsl

import agora.api.exchange.{SubmissionDetails, SubmitJob, Submitable}
import io.circe.Encoder

trait LowPriorityCirceSubmitable {

  implicit object SubmitableIdentity extends Submitable[SubmitJob] {
    override def asSubmitJob(request: SubmitJob) = request
  }

  implicit def asSubmitable[T](implicit encoder: Encoder[T],
                               details: SubmissionDetails = SubmissionDetails()): Submitable[T] = {
    new Submitable[T] {
      override def asSubmitJob(request: T) = {
        val asJson = implicitly[Encoder[T]]
        SubmitJob(details, asJson(request))
      }
    }
  }

  implicit def asJobSyntax[T](value: T)(implicit asSubmitable: Submitable[T]) =
    new AsJob(value, asSubmitable)
}
