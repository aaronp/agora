package jabroni.api.json

import io.circe.Decoder.Result
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._


sealed trait JPredicate {
  def matches(json: Json): Boolean

  /** @return the json representing this predicate
    */
  def json: Json

  def &&(other: JPredicate): JPredicate = And(this, other)

  def ||(other: JPredicate): JPredicate = Or(this, other)

  //  def encoder : Encoder[A]
}

object JPredicate {

  object implicits {

    implicit class RichJson(val field: String) extends AnyVal {
      private implicit def predAsJFilter(p: JPredicate): JFilter = {
        JFilter(field, p)
      }

      def !(other: JPredicate): JFilter = Not(other)

      def =!=[J <% Json](value: J): JFilter = Not(Eq(value))

      def !==[J <% Json](value: J): JFilter = Not(Eq(value))

      def ===[J <% Json](value: J): JFilter = Eq(value)

      def >(value: Json): JFilter = Gt(value)

      def <(value: Json): JFilter = Lt(value)

      def >=(value: Json): JFilter = Gte(value)

      def <=(value: Json): JFilter = Lte(value)

      def ~=(regex: String): JFilter = JRegex(regex)
    }

  }

  implicit object JPredicateFormat extends Encoder[JPredicate] with Decoder[JPredicate] {
    override def apply(c: HCursor): Result[JPredicate] = {
      import cats.syntax.either._
      val e: Either[DecodingFailure, JPredicate] = c.as[And].
        orElse(c.as[Or]).
        orElse(c.as[Or]).
        orElse(c.as[Not]).
        orElse(c.as[Eq]).
        orElse(c.as[JRegex])

      e.orElse(c.as[Gt]).
        orElse(c.as[Gte]).
        orElse(c.as[Lt]).
        orElse(c.as[Lte])
    }

    override def apply(a: JPredicate): Json = a match {
      case p: And => p.asJson
      case p: Or => p.asJson
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

case class Or(lhs: JPredicate, rhs: JPredicate) extends JPredicate {
  override def matches(json: Json) = lhs.matches(json) || rhs.matches(json)

  override def json: Json = this.asJson
}

case class And(lhs: JPredicate, rhs: JPredicate) extends JPredicate {
  override def matches(json: Json) = lhs.matches(json) && rhs.matches(json)

  override def json: Json = this.asJson
}

case class Not(predicate: JPredicate) extends JPredicate {
  override def matches(json: Json) = !(predicate.matches(json))

  override def json: Json = this.asJson
}

case class Eq(value: Json) extends JPredicate {
  override def matches(json: Json) = json == value

  override def json: Json = this.asJson
}

case class JRegex(regex: String) extends JPredicate {
  private val pattern = regex.r

  override def matches(json: Json) = json.asString.exists(v => pattern.findFirstIn(v).isDefined)

  override def json: Json = this.asJson
}

abstract class ComparablePredicate(value: Json, op: (Long, Long) => Boolean) extends JPredicate {

  import Ordering.Implicits._

  override def matches(json: Json) = {
    val res = json.as[Json].right.map { (tea: Json) =>
      (value.asNumber.flatMap(_.toLong), tea.asNumber.flatMap(_.toLong)) match {
        case (Some(x), Some(y)) => op(x, y)
        case _ => false
      }
    }
    res.right.getOrElse(false)
  }
}

import io.circe.{Decoder, Encoder, Json}

import Ordering.Implicits._

case class Gt(value: Json) extends ComparablePredicate(value, _ > _) {
  override def json: Json = this.asJson
}

case class Gte(value: Json) extends ComparablePredicate(value, _ >= _) {
  override def json: Json = this.asJson
}

case class Lt(value: Json) extends ComparablePredicate(value, _ < _) {
  override def json: Json = this.asJson
}

case class Lte(value: Json) extends ComparablePredicate(value, _ <= _) {
  override def json: Json = this.asJson
}
