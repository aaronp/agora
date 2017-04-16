package jabroni.api.json

import io.circe.Decoder.Result
import io.circe._

sealed trait JMatcher {
  def matches(json: Json): Boolean

  override def toString = getClass.getSimpleName.replaceAllLiterally("$", "")

  def &&(other: JMatcher) = and(other)

  def and(other: JMatcher) = JMatcher.And(this, other)

  def ||(other: JMatcher) = or(other)

  def or(other: JMatcher) = JMatcher.Or(this, other)
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
        case MatchAll => Json.fromString("MatchAll")
        case other => sys.error(s"$other not implemented")
      }
    }

    override def apply(c: HCursor): Result[JMatcher] = {
      val v = c.value
      val matchAllOpt: Option[JMatcher] = c.value.asString collect {
        case "MatchAll" => matchAll
      }

      matchAllOpt.
        toRight(DecodingFailure(s"Expected a json matcher", c.history))
    }
  }

}
