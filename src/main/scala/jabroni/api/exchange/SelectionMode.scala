package jabroni.api.exchange

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import jabroni.api
import jabroni.api.WorkRequestId
import jabroni.api.json.JPath
import jabroni.api.worker.RequestWork
import jabroni.domain.Take


abstract class SelectionMode(override val toString: String) {
  type Selected = SelectionMode.Selected
  type Remaining = SelectionMode.Remaining

  def select(offers: Stream[(api.WorkRequestId, RequestWork)]): (Selected, Remaining)

  def json: Json
}

// sends the work to the first matching eligible worker
case class SelectionFirst() extends SelectionMode("select-first") {
  override def select(offers: Stream[(WorkRequestId, RequestWork)]): (Selected, Remaining) = {
    offers.headOption match {
      case head@Some((_, offer)) if offer.itemsRequested == 1 => head.toStream -> offers.tail
      case Some((id, offer)) if offer.itemsRequested > 1 =>
        val remaining = (id, offer.dec) #:: offers.tail
        Stream(id -> offer.take(1)) -> remaining
      case None => Stream.empty -> Stream.empty
    }
  }

  override def json: Json = this.asJson
}

// sends the work to all eligible workers
case class SelectionAll() extends SelectionMode("select-all") {
  override def select(offers: Stream[(WorkRequestId, RequestWork)]) = offers -> Stream.empty

  override def json: Json = this.asJson
}

// sends the work to all eligible workers
case class SelectN(n: Int, fanOut: Boolean) extends SelectionMode(s"select-$n") {
  override def select(offers: Stream[(WorkRequestId, RequestWork)]): Stream[(WorkRequestId, RequestWork)] = {
    val reqByN = offers.map {
      case (id, req) => req.itemsRequested -> req
    }
    val foo: (Stream[(Int, RequestWork)], Stream[(Int, RequestWork)]) = Take[RequestWork, Stream[(Int, RequestWork)]](n, reqByN)
    
  }

  override def json: Json = this.asJson
}

// sends the work to all eligible workers
case class SelectIntMax(path: JPath) extends SelectionMode("select-int-nax") {
  override def select(offers: Stream[(WorkRequestId, RequestWork)]) = {
    val values: Stream[(api.WorkRequestId, RequestWork, Int)] = offers.flatMap {
      case (id, offer) =>
        path.apply(offer.worker.aboutMe).flatMap { value =>
          value.asNumber.flatMap(_.toInt).map { num =>
            (id, offer, num)
          }
        }
    }
    val (id, offer, _) = values.maxBy(_._3)

    offer.itemsRequested match {
      case 1 => Stream(id -> offer) -> offers.tail
      case n =>
        val remaining = (id -> offer.dec) #:: offers.tail
        Stream(id -> offer.take(1)) -> remaining
    }
  }

  override def json: Json = this.asJson
}

object SelectionMode {
  type Selection = Stream[(WorkRequestId, RequestWork)]
  type Selected = Selection
  type Remaining = Selection

  implicit object SelectionModeFormat extends Encoder[SelectionMode] with Decoder[SelectionMode] {
    override def apply(mode: SelectionMode): Json = mode.json

    override def apply(c: HCursor): Result[SelectionMode] = {
      import cats.syntax.either._

      c.as[SelectionFirst]
        .orElse(c.as[SelectionAll])
        .orElse(c.as[SelectN])
        .orElse(c.as[SelectIntMax])
    }
  }

}