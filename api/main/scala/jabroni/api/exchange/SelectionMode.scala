package jabroni.api.exchange


import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import jabroni.api.{SubscriptionKey, WorkRequestId}
import jabroni.api.json.JPath

import scala.collection.SeqLike
import scala.collection.generic.CanBuildFrom


abstract class SelectionMode(override val toString: String) {
  type Selected = SelectionMode.Selected
  type Remaining = SelectionMode.Remaining

  type Work = (SubscriptionKey, WorkSubscription, Int)
  //  def select(offers: Stream[(api.WorkRequestId, RequestWork)]): (Selected, Remaining)

  def select[Coll <: SeqLike[Work, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Work, Coll]): Coll

  def json: Json = Json.fromString(toString)
}

// sends the work to the first matching eligible worker
case class SelectionFirst() extends SelectionMode("select-first") {
  //  override def select(offers: Stream[(WorkRequestId, RequestWork)]): (Selected, Remaining) = {
  //
  //  values.headOption match {
  //      case head@Some(offer) if offer.itemsRequested == 1 => head.toStream -> offers.tail
  //      case Some((id, offer)) if offer.itemsRequested > 1 =>
  //        val remaining = (id, offer.dec) #:: offers.tail
  //        Stream(id -> offer.take(1)) -> remaining
  //      case None => Stream.empty -> Stream.empty
  //    }
  //  }
  //
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

  override def json: Json = Json.obj("select" -> Json.fromInt(n),
    "fanOut" -> Json.fromBoolean(fanOut))

  //
  //  override def select(offers: Stream[(WorkRequestId, RequestWork)]) = {
  //
  //    if (fanOut) {
  //      consumeFanOut(offers)
  //    } else {
  //      // eagerly consume work items from the head
  //      consumeFromHead(offers)
  //    }
  //  }

  override def select[Coll <: SeqLike[Work, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Work, Coll]): Coll = {
    if (fanOut) {
      values.distinct.take(n)
    } else {
      values.take(n)
    }
  }

  //
  //  /**
  //    * prefer to select different workers over multiple jobs to the same worker
  //    *
  //    * @param offers
  //    * @return
  //    */
  //  def consumeFanOut(offers: Stream[(WorkRequestId, RequestWork)]): (Selected, Remaining) = {
  //    // evenly distribute work items across all workers
  //    val (sel, rem) = offers.splitAt(n)
  //    val took = sel.map {
  //      case (id, offer) => (id, offer.take(1))
  //    }
  //    val putBack = sel.collect {
  //      case (id, offer) if offer.itemsRequested > 1 => (id, offer.dec)
  //    }
  //    val remaining = rem #::: putBack
  //
  //    if (sel.size < n && (rem.nonEmpty || putBack.nonEmpty)) {
  //      val (t2, r2) = consumeFanOut(rem ++ putBack)
  //      (took ++ t2) -> r2
  //    } else {
  //      (took, remaining)
  //    }
  //  }
  //
  //  def consumeFromHead(offers: Stream[(WorkRequestId, RequestWork)]): (Selected, Remaining) = {
  //    val reqByN: Stream[(Int, (WorkRequestId, RequestWork))] = offers.map {
  //      case pear@(_, req) => req.itemsRequested -> pear
  //    }
  //    // match up the number requested from the workers
  //    val (tookWithN, remainingWithN) = Take[(WorkRequestId, RequestWork), Stream[(Int, (WorkRequestId, RequestWork))]](n, reqByN)
  //
  //    val took = tookWithN.map(_._2)
  //    val remaining = {
  //      val newRemaining = remainingWithN.map(_._2)
  //      tookWithN.headOption match {
  //        case Some((numberRemaining, (id, offer))) if numberRemaining != offer.itemsRequested =>
  //          val newHead = (id, offer.copy(itemsRequested = numberRemaining))
  //          newHead #:: newRemaining.tail
  //        case None => newRemaining
  //      }
  //    }
  //    (took, remaining)
  //  }
}

// sends work to whichever has the maximum int value for the given property
case class SelectIntMax(path: JPath) extends SelectionMode("select-int-nax") {
  //  override def select(offers: Stream[(WorkRequestId, RequestWork)]) = {
  //  override def select[Coll <: SeqLike[T, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, T, Coll]): Coll = {
  //    val values: Stream[(api.WorkRequestId, RequestWork, Int)] = offers.flatMap {
  //      case (id, offer) =>
  //        path.apply(offer.worker.aboutMe).flatMap { value =>
  //          value.asNumber.flatMap(_.toInt).map { num =>
  //            (id, offer, num)
  //          }
  //        }
  //    }
  //    val (id, offer, _) = values.maxBy(_._3)
  //
  //    offer.itemsRequested match {
  //      case 1 => Stream(id -> offer) -> offers.tail
  //      case n =>
  //        val remaining = (id -> offer.dec) #:: offers.tail
  //        Stream(id -> offer.take(1)) -> remaining
  //    }
  //  }
  override def select[Coll <: SeqLike[Work, Coll]](collection: Coll)(implicit bf: CanBuildFrom[Coll, Work, Coll]): Coll = {
    val values = collection.flatMap {
      case pear@(_, work, n) =>
        path.apply(work.details.aboutMe).flatMap { value =>
          value.asNumber.flatMap(_.toInt).map { num =>
            (pear, num)
          }
        }
    }

    val (res, _) = values.maxBy(_._2)
    (bf() += res).result()
  }

  override def json: Json = Json.obj("max" -> path.json)
}

object SelectionMode {
  type Selection = Stream[(WorkRequestId, RequestWork)]
  type Selected = Selection
  type Remaining = Selection

  def first(): SelectionMode = SelectionFirst()

  def all(): SelectionMode = SelectionAll()

  def apply(n: Int, fanOut: Boolean = true): SelectionMode = SelectN(n, fanOut)

  def max(path: JPath): SelectionMode = SelectIntMax(path)

  //(implicit enc : Encoder, dec : Decoder)
  implicit def selectionModeFormat = new Encoder[SelectionMode] with Decoder[SelectionMode] {
    override def apply(mode: SelectionMode): Json = {
      mode.json
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

          //          import io.circe._
          //          import io.circe.generic.auto._
          //          val max = implicitly[Decoder[JPath]].tryDecode(c.downField("max")).map { path =>
          //            // FIXME - remove this cast
          //            SelectIntMax(path).asInstanceOf[SelectionMode]
          //          }
          //          max.orElse(asSelectN)
          ???
      }
    }
  }

}