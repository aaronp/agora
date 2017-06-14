package agora.api.json

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._

import io.circe.Json
import scala.language.implicitConversions
import scala.util.Try

sealed trait JPredicate {
  def matches(json: Json): Boolean

  /** @return the json representing this predicate
    */
  def json: Json
  def and(other: JPredicate, theRest: JPredicate*): JPredicate = And(this :: other :: theRest.toList)

  def or(other: JPredicate, theRest: JPredicate*): JPredicate = Or(this :: other :: theRest.toList)
}

object JPredicate {

  trait LowPriorityImplicits {

    implicit def stringAsJson(s: String) = Json.fromString(s)

    implicit def intAsJson(i: Int) = Json.fromInt(i)

    implicit class RichJson(field: String) {
      private implicit def predAsJFilter(p: JPredicate): JFilter = {
        JFilter(field, p)
      }

      def !(other: JPredicate): JFilter = Not(other)

      def =!=[J <% Json](value: J): JFilter = Not(Eq(value))

      def !==[J <% Json](value: J): JFilter = Not(Eq(value))

      def ===[J <% Json](value: J): JFilter = Eq(value)
      def equalTo[J <% Json](value: J): JFilter = Eq(value)

      def gt[J <% Json](value: J): JFilter = Gt(value)

      def lt[J <% Json](value: J): JFilter = Lt(value)

      def gte[J <% Json](value: J): JFilter = Gte(value)

      def lte[J <% Json](value: J): JFilter = Lte(value)

      def ~=(regex: String): JFilter = JRegex(regex)
    }

  }

  object implicits extends LowPriorityImplicits

  implicit object JPredicateFormat extends Encoder[JPredicate] with Decoder[JPredicate] {
    override def apply(c: HCursor): Result[JPredicate] = {
      import cats.syntax.either._


      c.as[And].
        orElse(c.as[Or]).
        orElse(c.as[Not]).
        orElse(c.as[Eq]).
        orElse(c.as[JRegex]).
        orElse(c.as[Gt]).
        orElse(c.as[Gte]).
        orElse(c.as[Lt]).
        orElse(c.as[Lte])
    }

    override def apply(a: JPredicate): Json = a match {
      case p: And => p.asJson
      case p: Or => p.asJson
      case p: Not => p.asJson
      case p: Eq => p.asJson
      case p: JRegex => p.asJson

      case p: Gt => p.asJson
      case p: Gte => p.asJson
      case p: Lt => p.asJson
      case p: Lte => p.asJson
    }
  }

}

case class Or(or: List[JPredicate]) extends JPredicate {
  override def matches(json: Json) = or.exists(_.matches(json))
  override def json = Json.obj("or" -> Json.fromValues(or.map(_.json)))
}

object Or {
  def apply(first: JPredicate, second: JPredicate, theRest: JPredicate*): Or = Or(first :: second :: theRest.toList)
}

case class And(and: List[JPredicate]) extends JPredicate {
  override def matches(json: Json) = and.forall(_.matches(json))
  override def json = Json.obj("and" -> Json.fromValues(and.map(_.json)))
}

object And {
  def apply(first: JPredicate, second: JPredicate, theRest: JPredicate*): And = And(first :: second :: theRest.toList)
}

case class Not(not: JPredicate) extends JPredicate {
  override def matches(json: Json) = !(not.matches(json))

  override def json: Json = this.asJson
}

case class Eq(eq: Json) extends JPredicate {
  override def matches(json: Json) = json == eq

  override def json: Json = this.asJson
}

case class JRegex(regex: String) extends JPredicate {
  private val pattern = regex.r

  override def matches(json: Json) = json.asString.exists(v => pattern.findFirstIn(v).isDefined)

  override def json: Json = this.asJson
}

sealed abstract class ComparablePredicate(value: Json, op: (Long, Long) => Boolean) extends JPredicate {

  import Ordering.Implicits._
  val refNum = asLong(value)

  private def asLong(json : Json) = {
    json.asNumber.flatMap(_.toLong).orElse {
      json.asString.flatMap(s => Try(s.toLong).toOption)
    }
  }
  override def matches(json: Json) = {
    val res = json.as[Json].right.map { (tea: Json) =>
      (asLong(tea), refNum) match {
        case (Some(x), Some(y)) => op(x, y)
        case _ => false
      }
    }
    res.right.getOrElse(false)
  }
}

import io.circe.{Decoder, Encoder, Json}

import Ordering.Implicits._

case class Gt(gt: Json) extends ComparablePredicate(gt, _ > _) {
  override def json: Json = this.asJson
}

case class Gte(gte: Json) extends ComparablePredicate(gte, _ >= _) {
  override def json: Json = this.asJson
}

case class Lt(lt: Json) extends ComparablePredicate(lt, _ < _) {
  override def json: Json = this.asJson
}

case class Lte(lte: Json) extends ComparablePredicate(lte, _ <= _) {
  override def json: Json = this.asJson
}
