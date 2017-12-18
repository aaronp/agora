package agora.api.exchange

import agora.api.json.{JExpression, JPath}
import cats.syntax.either._
import io.circe.Decoder.Result
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}

/**
  * Represents an update which should be made to the [[agora.api.worker.WorkerDetails]] when a job is matched against
  * a [[WorkSubscription]].
  *
  * Each matched worker will have its json update with the result of the [[agora.api.json.JExpression]] at
  * the given [[agora.api.json.JPath]]
  */
sealed trait OnMatchUpdateAction {
  def update(subscription: WorkSubscription): WorkSubscription
}

object OnMatchUpdateAction {
  def appendAction(value: JExpression, appendTo: JPath) = OnMatchAppend(value, appendTo)

  def removeAction(removePath: JPath) = OnMatchRemove(removePath)

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

case class OnMatchAppend(value: JExpression, appendTo: JPath) extends OnMatchUpdateAction {
  override def update(subscription: WorkSubscription): WorkSubscription = {
    subscription.withDetails { details =>
      val jsonToAppendOpt = value.eval(details.aboutMe)
      val updatedOpt = jsonToAppendOpt.map { value =>
        val jsonToMerge = JPath.selectJson(appendTo.path, value)

        details.copy(aboutMe = agora.api.json.deepMergeWithArrayConcat(details.aboutMe, jsonToMerge))
      }
      updatedOpt.getOrElse(details)
    }
  }
}

case class OnMatchRemove(removePath: JPath) extends OnMatchUpdateAction {
  override def update(subscription: WorkSubscription): WorkSubscription = {
    subscription.withDetails { details =>
      val sansPathOpt = removePath.removeFrom(details.aboutMe)
      val updatedOpt  = sansPathOpt.map(newJson => details.copy(aboutMe = newJson))
      updatedOpt.getOrElse(details)
    }
  }
}
