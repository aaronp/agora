package agora.api.exchange

import agora.api.SubscriptionKey
import agora.json.JPath
import io.circe.Decoder.Result
import io.circe._

import scala.collection.SeqLike
import scala.collection.generic.CanBuildFrom

/**
  * A SelectionMode determines which matched candidate(s) are chosen from an Exchange selection.
  *
  * For example, if there is a pool of 10 work subscriptions, and a job is submitted which matches three of them,
  * the SelectionMode determines which of the three are selected.
  *
  * Typically just the _best_ one of the list is chosen for some configurable meaning of _best_, though it could
  * select any number of work candidates.
  *
  * @param toString the selection mode description
  */
abstract class SelectionMode(override val toString: String) {
  type Selected  = SelectionMode.Selected
  type Remaining = SelectionMode.Remaining

  /**
    * Filter (choose) N of the input worker candidates
    *
    * @param values the candidate(s) to filter/select
    * @param bf
    * @tparam Coll
    * @return a collection of selected worker candidates
    */
  def select[Coll <: SeqLike[Candidate, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Candidate, Coll]): Coll

  /** @return true if this selection mode may choose multiple worker [[Candidate]]s
    */
  def selectsMultiple: Boolean = true
}

/**
  * chooses all the eligible (matched)  workers on the exchange.
  * The assumption is the work will be sent to all the workers, though the requesting client
  * will just return the first to finish
  */
case class SelectionFirst() extends SelectionMode("select-first") {
  override def select[Coll <: SeqLike[Candidate, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Candidate, Coll]): Coll = {
    values
  }
}

/**
  * just chooses one of the eligible workers
  */
case object SelectionOne extends SelectionMode("select-one") {
  override def select[Coll <: SeqLike[Candidate, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Candidate, Coll]): Coll = {
    values.take(1)
  }
  override val selectsMultiple: Boolean = false
}

// sends the work to all eligible workers
case object SelectionAll extends SelectionMode("select-all") {
  override def select[Coll <: SeqLike[Candidate, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Candidate, Coll]): Coll = {
    values
  }
}

// sends the work to all eligible workers
/**
  * @param n
  */
case class SelectN(n: Int) extends SelectionMode(s"select-$n") {

  override def selectsMultiple: Boolean = n > 1

  override def select[Coll <: SeqLike[Candidate, Coll]](values: Coll)(implicit bf: CanBuildFrom[Coll, Candidate, Coll]): Coll = {

    values.take(n)
  }
}

// sends work to whichever has the maximum value for the given property
case class SelectIntMax(path: JPath) extends SelectionMode("select-max") with SelectComparable {

  override def chooseOne(candidates: TraversableOnce[(Candidate, JsonNumber)]): Candidate = {
    val (res, _) = asIntCandidates(candidates).maxBy(_._2)
    res
  }
}

case class SelectIntMin(path: JPath) extends SelectionMode("select-min") with SelectComparable {

  override def chooseOne(candidates: TraversableOnce[(Candidate, JsonNumber)]): Candidate = {
    val (res, _) = asIntCandidates(candidates).minBy(_._2)
    res
  }
}

trait SelectComparable extends SelectionMode {
  def path: JPath

  def withNumericValues[Coll <: SeqLike[Candidate, Coll]](collection: Coll) = collection.flatMap {
    case pear @ Candidate(_, work, _) =>
      path.apply(work.details.aboutMe).flatMap { value =>
        value.asNumber.map { num =>
          (pear, num)
        }
      }
  }

  def asIntCandidates(candidates: TraversableOnce[(Candidate, JsonNumber)]): TraversableOnce[(Candidate, Int)] = {
    candidates.flatMap {
      case (c, jsNum) => jsNum.toInt.map(x => (c, x))
    }
  }

  def chooseOne(candidates: TraversableOnce[(Candidate, JsonNumber)]): Candidate

  override val selectsMultiple: Boolean = false

  override def select[Coll <: SeqLike[Candidate, Coll]](collection: Coll)(implicit bf: CanBuildFrom[Coll, Candidate, Coll]): Coll = {

    val values: TraversableOnce[(Candidate, JsonNumber)] = withNumericValues(collection)

    if (values.nonEmpty) {
      val res = chooseOne(values)
      (bf() += res).result()

    } else {
      bf().result()
    }
  }
}

object SelectionMode {
  type Selection = Stream[(SubscriptionKey, RequestWork)]
  type Selected  = Selection
  type Remaining = Selection
  type Work      = (SubscriptionKey, WorkSubscription, Int)

  def first(): SelectionMode   = SelectionFirst()
  def onlyOne(): SelectionMode = SelectionFirst()

  def all(): SelectionMode = SelectionAll

  def one(): SelectionMode = SelectionOne

  def apply(n: Int): SelectionMode = SelectN(n)

  def max(path: JPath): SelectionMode = SelectIntMax(path)

  def min(path: JPath): SelectionMode = SelectIntMin(path)

  implicit object SelectionModeFormat extends Encoder[SelectionMode] with Decoder[SelectionMode] {
    override def apply(mode: SelectionMode): Json = {
      mode match {
        case SelectN(n)         => Json.obj("select" -> Json.fromInt(n))
        case SelectIntMax(path) => Json.obj("max" -> path.json)
        case SelectIntMin(path) => Json.obj("min" -> path.json)
        case _                  => Json.fromString(mode.toString)
      }
    }

    override def apply(c: HCursor): Result[SelectionMode] = {
      import cats.syntax.either._

      def asSelectN: Result[SelectN] = {
        for {
          n <- c.downField("select").as[Int]
        } yield {
          SelectN(n.toInt)
        }
      }

      c.value.asString match {
        case Some("select-first") => Right(first())
        case Some("select-all")   => Right(all())
        case Some("select-one")   => Right(one())
        case _ =>
          import io.circe._
          import io.circe.generic.auto._
          val max = implicitly[Decoder[JPath]].tryDecode(c.downField("max")).map { path =>
            // FIXME - remove this cast
            SelectIntMax(path).asInstanceOf[SelectionMode]
          }
          val min = implicitly[Decoder[JPath]].tryDecode(c.downField("min")).map { path =>
            // FIXME - remove this cast
            SelectIntMin(path).asInstanceOf[SelectionMode]
          }
          max.orElse(min).orElse(asSelectN)
      }
    }
  }

}
