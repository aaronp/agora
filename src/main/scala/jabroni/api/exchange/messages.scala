package jabroni.api.exchange

import io.circe.Decoder.Result
import io.circe.HCursor
import io.circe.generic.auto._
import io.circe.syntax._
import jabroni.api.client.SubmitJob
import jabroni.api.json.JMatcher
import jabroni.api.worker.{SubscriptionKey, WorkerDetails}
import jabroni.api.{RequestSupport, ResponseSupport}

sealed trait SubscriptionRequest

sealed trait SubscriptionResponse

case class WorkSubscription(details: WorkerDetails,
                            workMatcher: JMatcher)(val onNext : (SubmitJob, Int) => Unit) extends SubscriptionRequest {
  def matches(job: SubmitJob)(implicit m: JobPredicate): Boolean = m.matches(job, this)

}

object WorkSubscription {

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
