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
  * we may even support wildcards, etc.
  *
  *
  */
sealed trait JPart {
  final def asPath = JPath(this)

  final def asMatcher = asPath.asMatcher

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

case class JField(name: String) extends JPart

case class JPos(pos: Int) extends JPart

case class JFilter(field: String, predicate: JPredicate) extends JPart
