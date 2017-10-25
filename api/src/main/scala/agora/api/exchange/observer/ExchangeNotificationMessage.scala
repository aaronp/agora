package agora.api.exchange.observer

import agora.api.JobId
import agora.api.exchange.{Candidate, QueueStateResponse, SubmitJob}
import agora.api.json.JsonDelta
import agora.api.time.Timestamp
import agora.api.worker.{CandidateSelection, SubscriptionKey}
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto._
import io.circe.java8.time._

sealed trait ExchangeNotificationMessage {
  def time: Timestamp
}

case class OnSubscriptionRequestCountChanged(override val time: Timestamp, id: SubscriptionKey, before: Int, after: Int) extends ExchangeNotificationMessage
case class OnSubscriptionUpdated(override val time: Timestamp, delta: JsonDelta, subscription: Candidate)                extends ExchangeNotificationMessage
case class OnJobSubmitted(override val time: Timestamp, job: SubmitJob)                                                  extends ExchangeNotificationMessage
case class OnSubscriptionCreated(override val time: Timestamp, subscription: Candidate)                                  extends ExchangeNotificationMessage
case class OnJobCancelled(override val time: Timestamp, jobId: JobId)                                                    extends ExchangeNotificationMessage
case class OnSubscriptionCancelled(override val time: Timestamp, subscriptionKey: SubscriptionKey)                       extends ExchangeNotificationMessage
case class OnMatch(override val time: Timestamp, jobId: JobId, job: SubmitJob, chosen: CandidateSelection)               extends ExchangeNotificationMessage
case class OnStateOfTheWorld(override val time: Timestamp, queueState: QueueStateResponse)                               extends ExchangeNotificationMessage

object ExchangeNotificationMessage {

  implicit val JsonDecoder: Decoder[ExchangeNotificationMessage] = {
    (implicitly[Decoder[OnSubscriptionRequestCountChanged]]
      .map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnSubscriptionUpdated]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnJobSubmitted]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnSubscriptionCreated]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnJobCancelled]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnSubscriptionCancelled]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnMatch]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnStateOfTheWorld]].map(x => x: ExchangeNotificationMessage))
  }

  implicit object JsonEncoder extends Encoder[ExchangeNotificationMessage] {
    override def apply(a: ExchangeNotificationMessage): Json = a match {
      case msg: OnSubscriptionRequestCountChanged => implicitly[Encoder[OnSubscriptionRequestCountChanged]].apply(msg)
      case msg: OnSubscriptionUpdated             => implicitly[Encoder[OnSubscriptionUpdated]].apply(msg)
      case msg: OnJobSubmitted                    => implicitly[Encoder[OnJobSubmitted]].apply(msg)
      case msg: OnSubscriptionCreated             => implicitly[Encoder[OnSubscriptionCreated]].apply(msg)
      case msg: OnJobCancelled                    => implicitly[Encoder[OnJobCancelled]].apply(msg)
      case msg: OnSubscriptionCancelled           => implicitly[Encoder[OnSubscriptionCancelled]].apply(msg)
      case msg: OnMatch                           => implicitly[Encoder[OnMatch]].apply(msg)
      case msg: OnStateOfTheWorld                 => implicitly[Encoder[OnStateOfTheWorld]].apply(msg)
    }
  }

  def unapply(json: String): Option[ExchangeNotificationMessage] = {
    import io.circe.parser._
    decode[ExchangeNotificationMessage](json).right.toOption
  }
}
