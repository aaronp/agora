package agora.api.exchange.observer

import agora.api.JobId
import agora.api.exchange.{Candidate, QueueStateResponse, SubmitJob}
import agora.json.JsonDelta
import agora.time.Timestamp
import agora.api.worker.{CandidateSelection, SubscriptionKey}
import io.circe._
import io.circe.generic.auto._
import io.circe.java8.time._

sealed trait ExchangeNotificationMessage {
  def time: Timestamp
}

case class OnSubscriptionRequestCountChanged(override val time: Timestamp,
                                             subscriptionRequestId: SubscriptionKey,
                                             requestCountBefore: Int,
                                             requestCountAfter: Int)
    extends ExchangeNotificationMessage

case class OnSubscriptionUpdated(override val time: Timestamp, subscriptionUpdate: JsonDelta, subscription: Candidate) extends ExchangeNotificationMessage

case class OnJobSubmitted(override val time: Timestamp, jobSubmitted: SubmitJob) extends ExchangeNotificationMessage

case class OnSubscriptionCreated(override val time: Timestamp, subscriptionCreated: Candidate) extends ExchangeNotificationMessage

case class OnJobsCancelled(override val time: Timestamp, cancelledJobIds: Set[JobId]) extends ExchangeNotificationMessage

case class OnSubscriptionsCancelled(override val time: Timestamp, cancelledSubscriptionKeys: Set[SubscriptionKey]) extends ExchangeNotificationMessage

case class OnMatch(override val time: Timestamp, matchedJobId: JobId, matchedJob: SubmitJob, selection: CandidateSelection) extends ExchangeNotificationMessage

case class OnStateOfTheWorld(override val time: Timestamp, stateOfTheWorld: QueueStateResponse) extends ExchangeNotificationMessage

object ExchangeNotificationMessage {

  implicit def JsonDecoder: Decoder[ExchangeNotificationMessage] = {
    implicitly[Decoder[OnSubscriptionRequestCountChanged]]
      .map(x => x: ExchangeNotificationMessage)
      .or(implicitly[Decoder[OnSubscriptionUpdated]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnJobSubmitted]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnSubscriptionCreated]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnJobsCancelled]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnSubscriptionsCancelled]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnMatch]].map(x => x: ExchangeNotificationMessage))
      .or(implicitly[Decoder[OnStateOfTheWorld]].map(x => x: ExchangeNotificationMessage))
  }

  implicit object JsonEncoder extends Encoder[ExchangeNotificationMessage] {
    override def apply(a: ExchangeNotificationMessage): Json = a match {
      case msg: OnSubscriptionRequestCountChanged => implicitly[Encoder[OnSubscriptionRequestCountChanged]].apply(msg)
      case msg: OnSubscriptionUpdated             => implicitly[Encoder[OnSubscriptionUpdated]].apply(msg)
      case msg: OnJobSubmitted                    => implicitly[Encoder[OnJobSubmitted]].apply(msg)
      case msg: OnSubscriptionCreated             => implicitly[Encoder[OnSubscriptionCreated]].apply(msg)
      case msg: OnJobsCancelled                   => implicitly[Encoder[OnJobsCancelled]].apply(msg)
      case msg: OnSubscriptionsCancelled          => implicitly[Encoder[OnSubscriptionsCancelled]].apply(msg)
      case msg: OnMatch                           => implicitly[Encoder[OnMatch]].apply(msg)
      case msg: OnStateOfTheWorld                 => implicitly[Encoder[OnStateOfTheWorld]].apply(msg)
    }
  }

  def unapply(json: String): Option[ExchangeNotificationMessage] = {
    import io.circe.parser._
    decode[ExchangeNotificationMessage](json).right.toOption
  }
}
