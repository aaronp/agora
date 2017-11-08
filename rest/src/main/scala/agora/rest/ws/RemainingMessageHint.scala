package agora.rest.ws

import io.circe.Decoder.Result
import io.circe._

sealed trait RemainingMessageHint
object RemainingMessageHint {
  def single        = KnownMessages(0)
  def unknown       = Unknown
  def apply(n: Int) = KnownMessages(n)

  implicit object Format extends Encoder[RemainingMessageHint] with Decoder[RemainingMessageHint] {
    override def apply(a: RemainingMessageHint): Json = {
      a match {
        case Unknown          => Json.fromString("unknown")
        case KnownMessages(n) => Json.fromInt(n)
      }
    }

    override def apply(c: HCursor): Result[RemainingMessageHint] = {
      import cats.syntax.either._

      val finite = c.as[String].flatMap {
        case "unknown" => Right[DecodingFailure, RemainingMessageHint](Unknown)
        case other     => Left(DecodingFailure(s"couldn't decipher '${other}' as remaining message hint", c.history))
      }

      finite.orElse {
        val intRes = Decoder[Int].tryDecode(c)
        intRes.map(KnownMessages.apply)
      }
    }
  }
}

case class KnownMessages(remainingMessages: Int) extends RemainingMessageHint
case object Unknown                              extends RemainingMessageHint
