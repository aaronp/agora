package agora.api.exchange

import agora.api.json.{JConstantExpression, JExpression, JMergeExpression, JNumericExpression, JPath, JPathExpression, JStringExpression}
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.syntax._
import cats.syntax.either._

/**
  * Represents an update which should be made to the [[agora.api.worker.WorkerDetails]] when a job is matched against
  * a [[WorkSubscription]].
  *
  * Each matched worker will have its json update with the result of the [[agora.api.json.JExpression]] at
  * the given [[agora.api.json.JPath]]
  */
sealed trait OnMatchUpdateAction
object OnMatchUpdateAction {
  def appendAction(value: JExpression, appendTo: JPath) = OnMatchAppend(value, appendTo)
  def removeAction(removePath: JPath)                   = OnMatchRemove(removePath)

  implicit object JsonFormat extends Encoder[OnMatchUpdateAction] with Decoder[OnMatchUpdateAction] {
    override def apply(exp: OnMatchUpdateAction): Json = {
      import io.circe.generic.auto._
      exp match {
        case value: OnMatchAppend => value.asJson
        case value: OnMatchRemove => value.asJson
      }
    }

    override def apply(c: HCursor): Result[OnMatchUpdateAction] = {
      import io.circe.generic.auto._
      c.as[OnMatchAppend].orElse(c.as[OnMatchRemove])
    }
  }
}

case class OnMatchAppend(value: JExpression, appendTo: JPath) extends OnMatchUpdateAction
case class OnMatchRemove(removePath: JPath)                   extends OnMatchUpdateAction
