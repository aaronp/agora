package agora.rest.ws

import io.circe.Decoder.Result
import io.circe._

sealed trait RemainingMessageHint
object RemainingMessageHint {
  def single        = NoMoreMessages
  def unknown       = NoMoreMessages
  def apply(n: Int) = KnownMessages(n)

  implicit object Format extends Encoder[RemainingMessageHint] with Decoder[RemainingMessageHint] {
    override def apply(a: RemainingMessageHint): Json = {
      a match {
        case Unknown          => Json.fromString("unknown")
        case NoMoreMessages   => Json.fromString("last")
        case KnownMessages(n) => Json.fromInt(n)
      }
    }

    override def apply(c: HCursor): Result[RemainingMessageHint] = {
      import cats.syntax.either._

      val finite = c.as[String].flatMap {
        case "unknown" => Right[DecodingFailure, RemainingMessageHint](Unknown)
        case "last"    => Right[DecodingFailure, RemainingMessageHint](NoMoreMessages)
        case other     => Left(DecodingFailure(s"couldn't decipher '${other}' as remaining message hint", c.history))
      }

      finite.orElse {
        Decoder[Int].tryDecode(c).map(KnownMessages.apply)
      }
    }
  }
}

case object NoMoreMessages                       extends RemainingMessageHint
case class KnownMessages(remainingMessages: Int) extends RemainingMessageHint
case object Unknown                              extends RemainingMessageHint
