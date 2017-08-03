package agora.api.json

import io.circe.Decoder.Result
import io.circe._

sealed trait JMatcher {
  def matches(json: Json): Boolean

  override def toString = getClass.getSimpleName.replaceAllLiterally("$", "")

  def and(other: JMatcher): JMatcher    = MatchAnd(this, other)
  final def and(other: JPart): JMatcher = and(other.asMatcher)
  final def and(other: JPath): JMatcher = and(other.asMatcher)

  def or(other: JMatcher): JMatcher    = MatchOr(this, other)
  final def or(other: JPart): JMatcher = or(other.asMatcher)
  final def or(other: JPath): JMatcher = or(other.asMatcher)

}

object JMatcher {

  def apply(jpath: JPath): JMatcher = ExistsMatcher(jpath)

  def matchAll: JMatcher = MatchAll
  def matchNone: JMatcher = MatchNone


  implicit def filterAsMatcher(filter: JFilter): JMatcher = filter.asMatcher
  implicit def pathAsMatcher(jpath: JPath): JMatcher = jpath.asMatcher

  implicit object JMatcherJson extends Encoder[JMatcher] with Decoder[JMatcher] {
    override def apply(jsonMatcher: JMatcher): Json = {
      jsonMatcher match {
        case MatchAll           => Json.fromString("match-all")
        case MatchAnd(lhs, rhs) => Json.obj("and" -> Json.obj("lhs" -> apply(lhs), "rhs" -> apply(rhs)))
        case MatchOr(lhs, rhs)  => Json.obj("or" -> Json.obj("lhs" -> apply(lhs), "rhs" -> apply(rhs)))
        case em @ ExistsMatcher(jpath) =>
          import io.circe.syntax._
          import io.circe.generic._
          import io.circe.generic.auto._
          Json.obj("exists" -> jpath.asJson)
        case other => sys.error(s"$other not implemented")
      }
    }

    private def asConjunction(c: ACursor)(make: (JMatcher, JMatcher) => JMatcher): Result[JMatcher] = {
      import cats.syntax.either._
      for {
        lhs <- c.downField("lhs").as[JMatcher]
        rhs <- c.downField("rhs").as[JMatcher]
      } yield {
        make(lhs, rhs)
      }
    }

    override def apply(c: HCursor): Result[JMatcher] = {
      import cats.syntax.either._

      def asAnd = asConjunction(c.downField("and"))(MatchAnd.apply)

      def asOr = asConjunction(c.downField("or"))(MatchOr.apply)

      def asMatchAll: Result[JMatcher] = c.as[String] match {
        case Right("match-all") => Right(MatchAll): Result[JMatcher]
        case Right(other)       => Left(DecodingFailure(s"Expected 'match-all', but got '$other'", c.history)): Result[JMatcher]
        case left: Result[_]    => left.asInstanceOf[Result[JMatcher]]
      }

      import io.circe.generic.auto._
      import io.circe.generic._
      val exists: Result[JMatcher] = c.downField("exists").as[JPath].map(p => ExistsMatcher(p))
      exists.orElse(asAnd).orElse(asOr).orElse(asMatchAll)
    }
  }

}

case class ExistsMatcher(jpath: JPath) extends JMatcher {
  override def matches(json: Json): Boolean = {
    jpath(json).isDefined
  }
  override def toString = s"Exists($jpath)"
}

object MatchAll extends JMatcher {
  override def matches(json: Json): Boolean = true
}
object MatchNone extends JMatcher {
  override def matches(json: Json): Boolean = false
}

case class MatchOr(lhs: JMatcher, rhs: JMatcher) extends JMatcher {
  override def matches(json: Json): Boolean = lhs.matches(json) || rhs.matches(json)
  override def toString                     = s"($lhs || $rhs)"
}

case class MatchAnd(lhs: JMatcher, rhs: JMatcher) extends JMatcher {
  override def matches(json: Json): Boolean = lhs.matches(json) && rhs.matches(json)
  override def toString                     = s"($lhs && $rhs)"
}
