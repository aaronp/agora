package agora.api.json

import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto.{exportDecoder, exportEncoder}
import io.circe.syntax._

sealed trait JMatcher {
  def matches(json: Json): Boolean

  override def toString = getClass.getSimpleName.replaceAllLiterally("$", "")

  def and(other: JMatcher): JMatcher = MatchAnd(this, other)

  final def and(other: JPart): JMatcher = and(other.asMatcher)

  final def and(other: JPath): JMatcher = and(other.asMatcher)

  def or(other: JMatcher): JMatcher = MatchOr(this, other)

  final def or(other: JPart): JMatcher = or(other.asMatcher)

  final def or(other: JPath): JMatcher = or(other.asMatcher)

  final def asNot = MatchNot(this)
}

object JMatcher {

  def apply(jpath: JPath): JMatcher = ExistsMatcher(jpath)

  def matchAll: JMatcher = MatchAll

  def matchNone: JMatcher = MatchNone

  implicit def filterAsMatcher(filter: JFilter): JMatcher = filter.asMatcher

  implicit def pathAsMatcher(jpath: JPath): JMatcher = jpath.asMatcher

  implicit class RichDec[T](val result: Result[T]) extends AnyVal {
    def orDecode[A <: T: Decoder](c: HCursor): Result[T] = {
      val aDec: Decoder[A] = implicitly[Decoder[A]]
      result.left.flatMap { _ =>
        aDec.tryDecode(c)
      }
    }
  }
  implicit object JMatcherJson extends Encoder[JMatcher] with Decoder[JMatcher] {
    override def apply(jsonMatcher: JMatcher): Json = {
      jsonMatcher match {
        case MatchAll      => Json.fromString("match-all")
        case MatchNone     => Json.fromString("match-none")
        case not: MatchNot => not.asJson
        case and: MatchAnd => and.asJson
        case or: MatchOr   => or.asJson
        case _ @ExistsMatcher(jpath) =>
          import io.circe.generic.auto._
          import io.circe.syntax._
          Json.obj("exists" -> jpath.asJson)
        case other => sys.error(s"$other not implemented")
      }
    }

    override def apply(c: HCursor): Result[JMatcher] = {
      import cats.syntax.either._

      def asMatchAll: Result[JMatcher] = c.as[String] match {
        case Right("match-all") => Right(MatchAll): Result[JMatcher]
        case Right(other)       => Left(DecodingFailure(s"Expected 'match-all', but got '$other'", c.history)): Result[JMatcher]
        case left: Result[_]    => left.asInstanceOf[Result[JMatcher]]
      }

      def asMatchNone: Result[JMatcher] = c.as[String] match {
        case Right("match-none") => Right(MatchNone): Result[JMatcher]
        case Right(other)        => Left(DecodingFailure(s"Expected 'match-all', but got '$other'", c.history)): Result[JMatcher]
        case left: Result[_]     => left.asInstanceOf[Result[JMatcher]]
      }

      import io.circe.generic.auto._
      val exists: Result[JMatcher] = c.downField("exists").as[JPath].map(p => ExistsMatcher(p))
      exists.orElse(asMatchAll).orDecode[MatchAnd](c).orDecode[MatchOr](c).orDecode[MatchNot](c).orElse(asMatchNone)
    }
  }

}

case class ExistsMatcher(jpath: JPath) extends JMatcher {
  override def matches(json: Json): Boolean = {
    jpath(json).isDefined
  }

  override def toString = s"Exists($jpath)"
}

case class MatchNot(not: JMatcher) extends JMatcher {
  override def matches(json: Json): Boolean = {
    !not.matches(json)
  }

  override def toString = s"not($not)"
}

object MatchAll extends JMatcher {
  override def matches(json: Json): Boolean = true
}

object MatchNone extends JMatcher {
  override def matches(json: Json): Boolean = false
}

case class MatchOr(or: List[JMatcher]) extends JMatcher {
  override def matches(json: Json): Boolean = or.exists(_.matches(json))

  override def toString = or.mkString("(", " || ", "")
}

object MatchOr {
  def apply(first: JMatcher, second: JMatcher, theRest: JMatcher*): MatchOr = MatchOr(first :: second :: theRest.toList)

  implicit object Format extends Encoder[MatchOr] with Decoder[MatchOr] {
    override def apply(a: MatchOr): Json = {
      val array = Json.arr(a.or.map(_.asJson): _*)
      Json.obj("or" -> array)
    }

    override def apply(c: HCursor): Result[MatchOr] = {
      c.downField("or").as[List[JMatcher]].right.map(list => MatchOr(list.toList))
    }
  }
}

case class MatchAnd(and: List[JMatcher]) extends JMatcher {
  override def matches(json: Json): Boolean = and.forall(_.matches(json))

  override def toString = and.mkString("(", " && ", "")
}

object MatchAnd {
  def apply(first: JMatcher, second: JMatcher, theRest: JMatcher*): MatchAnd = MatchAnd(first :: second :: theRest.toList)

  implicit object Format extends Encoder[MatchAnd] with Decoder[MatchAnd] {
    override def apply(a: MatchAnd): Json = {
      val array = Json.arr(a.and.map(_.asJson): _*)
      Json.obj("and" -> array)
    }

    override def apply(c: HCursor): Result[MatchAnd] = {
      c.downField("and").as[List[JMatcher]].right.map(list => MatchAnd(list.toList))
    }
  }
}
