package jabroni.api.json

import io.circe.Decoder.Result
import io.circe._

sealed trait JMatcher {
  def matches(json: Json): Boolean

  override def toString = getClass.getSimpleName.replaceAllLiterally("$", "")

  def &&(other: JMatcher): JMatcher = and(other)

  def and(other: JMatcher): JMatcher = JMatcher.And(this, other)

  def ||(other: JMatcher): JMatcher = or(other)

  def or(other: JMatcher): JMatcher = JMatcher.Or(this, other)
}

object JMatcher {

  def matchAll: JMatcher = MatchAll

  case class ExistsMatcher(jpath: JPath) extends JMatcher {
    override def matches(json: Json): Boolean = {
      jpath(json).isDefined
    }
  }


  def apply(jpath: JPath): JMatcher = ExistsMatcher(jpath)

  object MatchAll extends JMatcher {
    override def matches(json: Json): Boolean = true
  }

  case class Or(lhs: JMatcher, rhs: JMatcher) extends JMatcher {
    override def matches(json: Json): Boolean = lhs.matches(json) || rhs.matches(json)
  }

  case class And(lhs: JMatcher, rhs: JMatcher) extends JMatcher {
    override def matches(json: Json): Boolean = lhs.matches(json) && rhs.matches(json)
  }

  implicit object JMatcherJson extends Encoder[JMatcher] with Decoder[JMatcher] {
    override def apply(jsonMatcher: JMatcher): Json = {
      jsonMatcher match {
        case MatchAll => Json.fromString("match-all")
        case And(lhs, rhs) => Json.obj("and" -> Json.obj("lhs" -> apply(lhs), "rhs" -> apply(rhs)))
        case Or(lhs, rhs) => Json.obj("or" -> Json.obj("lhs" -> apply(lhs), "rhs" -> apply(rhs)))
        case ExistsMatcher(jpath) =>
          import io.circe.syntax._
          import io.circe.generic._
          import io.circe.generic.auto._
          Json.obj("exists" -> jpath.asJson)
        case other => sys.error(s"$other not implemented")
      }
    }

    override def apply(c: HCursor): Result[JMatcher] = {
      val matchAllOpt: Option[JMatcher] = c.value.asString collect {
        case "match-all" => matchAll
        case "and" => matchAll
        case "or" => matchAll
      }

      matchAllOpt.
        toRight(DecodingFailure(s"Expected a json matcher", c.history))
    }
  }

}
