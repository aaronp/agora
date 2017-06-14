package agora.api.exchange


import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import agora.api.SubscriptionKey
import agora.api.json.JPath

import scala.collection.SeqLike
import scala.collection.generic.CanBuildFrom


abstract class SelectionMode(override val toString: String) {
  type Selected = SelectionMode.Selected
  type Remaining = SelectionMode.Remaining

  type Work = SelectionMode.Work

  def select[Coll <: SeqLike[Work, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Work, Coll]): Coll

  //  def json: Json = Json.fromString(toString)
}

// sends the work to the first matching eligible worker
case class SelectionFirst() extends SelectionMode("select-first") {
  override def select[Coll <: SeqLike[Work, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Work, Coll]): Coll = {
    values.take(1)
  }
}

// sends the work to all eligible workers
case class SelectionAll() extends SelectionMode("select-all") {
  //  override def select(offers: Stream[(WorkRequestId, RequestWork)]) = offers -> Stream.empty

  override def select[Coll <: SeqLike[Work, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Work, Coll]): Coll = {
    values
  }
}

// sends the work to all eligible workers
case class SelectN(n: Int, fanOut: Boolean) extends SelectionMode(s"select-$n") {

  override def select[Coll <: SeqLike[Work, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Work, Coll]): Coll = {
    if (fanOut) {
      values.distinct.take(n)
    } else {
      values.take(n)
    }
  }
}

// sends work to whichever has the maximum int value for the given property
case class SelectIntMax(path: JPath) extends SelectionMode("select-int-nax") {

  override def select[Coll <: SeqLike[Work, Coll]](collection: Coll)(implicit bf: CanBuildFrom[Coll, Work, Coll]): Coll = {
    val values = collection.flatMap {
      case pear@(_, work, n) =>
        path.apply(work.details.aboutMe).flatMap { value =>
          value.asNumber.flatMap(_.toInt).map { num =>
            (pear, num)
          }
        }
    }

    if (values.nonEmpty) {
      val (res, _) = values.maxBy(_._2)
      (bf() += res).result()

    } else {
      bf().result()
    }

  }

}

object SelectionMode {
  type Selection = Stream[(SubscriptionKey, RequestWork)]
  type Selected = Selection
  type Remaining = Selection
  type Work = (SubscriptionKey, WorkSubscription, Int)

  def first(): SelectionMode = SelectionFirst()

  def all(): SelectionMode = SelectionAll()

  def apply(n: Int, fanOut: Boolean = true): SelectionMode = SelectN(n, fanOut)

  def max(path: JPath): SelectionMode = SelectIntMax(path)

  implicit object SelectionModeFormat extends Encoder[SelectionMode] with Decoder[SelectionMode] {
    override def apply(mode: SelectionMode): Json = {
      mode match {
        case SelectN(n, fanOut) => Json.obj("select" -> Json.fromInt(n), "fanOut" -> Json.fromBoolean(fanOut))
        case SelectIntMax(path) => Json.obj("max" -> path.json)
        case _ => Json.fromString(mode.toString)
      }
    }

    override def apply(c: HCursor): Result[SelectionMode] = {
      import cats.syntax.either._

      def asSelectN: Result[SelectN] = {
        for {
          n <- c.downField("select").as[Int]
          fanOut <- c.downField("fanOut").as[Boolean]
        } yield {
          SelectN(n.toInt, fanOut.booleanValue())
        }
      }

      c.value.asString match {
        case Some("select-first") => Right(first())
        case Some("select-all") => Right(all())
        case _ =>
          import io.circe._
          import io.circe.generic.auto._
          val max = implicitly[Decoder[JPath]].tryDecode(c.downField("max")).map { path =>
            // FIXME - remove this cast
            SelectIntMax(path).asInstanceOf[SelectionMode]
          }
          max.orElse(asSelectN)
      }
    }
  }

}
