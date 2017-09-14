package agora.api.json

import io.circe.Decoder.Result
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.optics.JsonPath

/**
  * represents part of a json path
  * e.g.
  *
  * foo/2/{x == 3}
  *
  * would be represented as
  *
  * JField("foo") :: JPos(2) :: JFilterValue("x", 3)  :: Nil
  *
  */
sealed trait JPart {

  /** @return this part as a complete path
    */
  final def asPath = JPath(this)

  /** @return this part as a complete matcher
    */
  final def asMatcher: JMatcher = asPath.asMatcher

  final def and(other: JMatcher): JMatcher = asMatcher.and(other)

  final def and(other: JPart): JMatcher = and(other.asMatcher)

  final def or(other: JMatcher): JMatcher = asMatcher.or(other)

  final def or(other: JPart): JMatcher = or(other.asMatcher)
}

object JPart {

  def apply(name: String) = JField(name)

  def apply(i: Int) = JPos(i)

  def apply(field: String, predicate: JPredicate) = JFilter(field, predicate)

  import cats.syntax.either._

  import JPredicate._

  object JFilterDec extends Decoder[JFilter] {
    override def apply(c: HCursor): Result[JFilter] = {
      val fldCurs = c.downField("field").as[String]
      val prdCurs = c.downField("predicate").as[JPredicate](JPredicate.JPredicateFormat)
      fldCurs.flatMap { (fld: String) =>
        prdCurs.map { (prd: JPredicate) =>
          JFilter(fld, prd)
        }
      }
    }
  }

  implicit object JPartFormat extends Decoder[JPart] with Encoder[JPart] {
    override def apply(c: HCursor): Result[JPart] = {
      val jposDec   = implicitly[Decoder[JPos]]
      val jfieldDec = implicitly[Decoder[JField]]
      jfieldDec.tryDecode(c).orElse(jposDec.tryDecode(c)).orElse(JFilterDec.tryDecode(c))

    }

    override def apply(part: JPart): Json = {
      part match {
        case filter: JFilter =>
          Json.obj("field" -> Json.fromString(filter.field), "predicate" -> filter.predicate.json)
        case field: JField => field.asJson
        case pos: JPos     => pos.asJson
      }
    }
  }

}

/**
  * Represents a json field (e.g. the 'foo' in 'foo.bar')
  * @param name the field name
  */
case class JField(name: String) extends JPart

/**
  * Represents a position in an array
  * @param pos the array index
  */
case class JPos(pos: Int) extends JPart

/**
  * Represents a predicate for a particular field
  * @param field the json field
  * @param predicate the predicate to evaluate against the field
  */
case class JFilter(field: String, predicate: JPredicate) extends JPart
