package agora.rest.ws

import akka.http.scaladsl.model.ws.TextMessage
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}

import scala.reflect.ClassTag

case class WebSocketEnvelope[T: Encoder](msgId: String, value: T, remaining: RemainingMessageHint)

object WebSocketEnvelope {

  /** @param value the value to encode
    * @param msgId    the message ID
    * @tparam T the value to send
    * @return a TextMessage with the given value and id
    */
  def textMessage[T: Encoder: Decoder: ClassTag](value: T,
                                                 remaining: RemainingMessageHint = RemainingMessageHint.single,
                                                 msgId: MsgId = nextMsgId()): TextMessage.Strict = {
    val json = apply(value, remaining, msgId).asJson.noSpaces
    TextMessage(json)
  }

  /** @return A WebSocketEnvelope for the given value
    */
  def apply[T: Encoder](value: T, remaining: RemainingMessageHint = RemainingMessageHint.single, msgID: MsgId = nextMsgId()): WebSocketEnvelope[T] = {
    new WebSocketEnvelope(msgID, value, remaining)
  }

  implicit def wsEnvDecoder[T: Encoder: Decoder]: Decoder[WebSocketEnvelope[T]] = {
    Decoder.instance[WebSocketEnvelope[T]] { cursor =>
      import cats.syntax.either._

      for {
        msgId <- Decoder[MsgId].tryDecode(cursor.downField("msgId"))
        hint  <- Decoder[RemainingMessageHint].tryDecode(cursor.downField("remaining"))
        value <- Decoder[T].tryDecode(cursor.downField("value"))
      } yield {
        WebSocketEnvelope(msgId, value, hint)
      }
    }
  }

  implicit def wsEnvEncoder[T: Encoder: Decoder]: Encoder[WebSocketEnvelope[T]] = {
    Encoder.instance[WebSocketEnvelope[T]] { envelope =>
      Json.obj("msgId" -> envelope.msgId.asJson, "value" -> envelope.value.asJson, "remaining" -> envelope.remaining.asJson)
    }
  }
//  def unapply[T: Encoder: Decoder](json: String): Option[(MsgId, T, RemainingMessageHint)] = {
////    FromJsonString.unapply(json).map {
////      case WebSocketEnvelope(msgId, value, remaining) => (msgId, value, remaining)
////    }
//    decode[WebSocketEnvelope[T]](json).right.toOption.map { env =>
//      (env.msgId, env.value, env.remaining)
//
//    }
//  }
}
