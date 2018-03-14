package agora.json

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._

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
  final def asMatcher(filter: JPredicate = JPredicate.matchAll): JPredicate = asPath.asMatcher(filter)

  final def and(other: JPredicate): JPredicate = asMatcher().and(other)

  final def and(other: JPart): JPredicate = and(other.asMatcher())

  final def or(other: JPredicate): JPredicate = asMatcher().or(other)

  final def or(other: JPart): JPredicate = or(other.asMatcher())
}

object JPart {

  def apply(name: String) = JField(name)

  def apply(i: Int) = JPos(i)

  def apply(field: String, predicate: JPredicate) = JFilter(field, predicate)

  import cats.syntax.either._

  implicit def intAsPos(pos: Int) = JPos(pos)

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
      val jposDec          = implicitly[Decoder[Int]].map(JPos.apply)
      val arrayContainsDec = implicitly[Decoder[JArrayFind]]
      val jfieldDec        = implicitly[Decoder[String]].map(JField.apply)
      jfieldDec
        .tryDecode(c)
        .orElse(jposDec.tryDecode(c))
        .orElse(arrayContainsDec.tryDecode(c))
        .orElse(JFilterDec.tryDecode(c))

    }

    override def apply(part: JPart): Json = {
      part match {
        case filter: JFilter =>
          Json.obj("field" -> Json.fromString(filter.field), "predicate" -> filter.predicate.json)
        case JField(field)   => field.asJson
        case JPos(pos)       => pos.asJson
        case pos: JArrayFind => pos.asJson
      }
    }
  }

}

/**
  * Represents a json field (e.g. the 'foo' in 'foo.bar')
  *
  * @param name the field name
  */
case class JField(name: String) extends JPart

/**
  * Represents a position in an array
  *
  * @param pos the array index
  */
case class JPos(pos: Int) extends JPart

/**
  * Represents a json value within an array which matches the 'arrayFind' predicate
  *
  * @param arrayFind the value to find within an array
  */
case class JArrayFind(arrayFind: JPredicate) extends JPart

/**
  * Represents a predicate for a particular field
  *
  * @param field     the json field
  * @param predicate the predicate to evaluate against the field
  */
case class JFilter(field: String, predicate: JPredicate) extends JPart
