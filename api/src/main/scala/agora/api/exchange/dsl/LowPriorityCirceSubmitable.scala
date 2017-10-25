package agora.api.exchange.dsl

import agora.api.exchange.{Exchange, SubmissionDetails, SubmitJob, Submitable}
import io.circe.Encoder

trait LowPriorityCirceSubmitable {

  implicit object SubmitableIdentity extends Submitable[SubmitJob] {
    override def asSubmitJob(request: SubmitJob) = request
  }

  implicit def asSubmitable[T](implicit encoder: Encoder[T], details: SubmissionDetails = SubmissionDetails()): Submitable[T] = {
    new Submitable[T] {
      override def asSubmitJob(request: T) = {
        val asJson = implicitly[Encoder[T]]
        SubmitJob(details, asJson(request))
      }
    }
  }

  implicit class AsJobSyntax[T](value: T) {
    implicit def asJob(implicit s: Submitable[T]) = s.asSubmitJob(value)
    implicit def asJob(details: SubmissionDetails)(implicit s: Submitable[T]) = {
      s.asSubmitJob(value).withDetails(details)
    }
  }

  /**
    * Adds 'enqueue' methods to an exchange
    */
  implicit class RichExchange(override val exchange: Exchange) extends JobSyntax

}
