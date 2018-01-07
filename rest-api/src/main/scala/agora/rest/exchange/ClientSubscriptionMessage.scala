package agora.rest.exchange

import cats.Semigroup
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

/**
  * The representation of a remote message sent from a [[org.reactivestreams.Subscriber]]
  */
sealed trait ClientSubscriptionMessage

case class TakeNext(take: Long) extends ClientSubscriptionMessage

case object Cancel extends ClientSubscriptionMessage {
  def jsonRepr = Json.fromString("cancel")
}

object ClientSubscriptionMessage {
  def unapply(json: String): Option[ClientSubscriptionMessage] = {
    decode[ClientSubscriptionMessage](json).right.toOption
  }

  def takeNext(take: Long) = TakeNext(take)

  def cancel = Cancel

  /**
    * A semigroup which will consider any cancel message over a 'TakeNext' message
    */
  implicit object ClientSubscriptionMessageSemigroup extends Semigroup[ClientSubscriptionMessage] {
    override def combine(x: ClientSubscriptionMessage, y: ClientSubscriptionMessage): ClientSubscriptionMessage = {
      (x, y) match {
        case (TakeNext(a), TakeNext(b)) => TakeNext(a + b)
        case _ => Cancel
      }
    }
  }

  implicit val JsonDecoder: Decoder[ClientSubscriptionMessage] = {
    val cancelDecoder = Decoder.decodeString.flatMap {
      case "cancel" =>
        Decoder.instance[ClientSubscriptionMessage](_ => Right(Cancel))
      case other =>
        Decoder.instance[ClientSubscriptionMessage](cursor => Left(DecodingFailure(s"Expected 'cancel', but got $other", cursor.history)))
    }
    implicitly[Decoder[TakeNext]].map(x => x: ClientSubscriptionMessage).or(cancelDecoder)
  }

  implicit object JsonEncoder extends Encoder[ClientSubscriptionMessage] {
    override def apply(a: ClientSubscriptionMessage): Json = a match {
      case Cancel => Cancel.jsonRepr
      case msg: TakeNext => implicitly[Encoder[TakeNext]].apply(msg)
    }
  }

}
