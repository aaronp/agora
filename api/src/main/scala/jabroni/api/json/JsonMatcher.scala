package jabroni.api.json

import io.circe.Decoder.Result
import io.circe._

sealed trait JsonMatcher {
  def matches(json: Json): Boolean

  override def toString = getClass.getSimpleName.replaceAllLiterally("$", "")
}

object JsonMatcher {

  def matchAll: JsonMatcher = MatchAll

  object MatchAll extends JsonMatcher {
    override def matches(json: Json): Boolean = true
  }

  case class Regex(lhs: JsonMatcher, rhs: JsonMatcher) extends JsonMatcher {
    override def matches(json: Json): Boolean = lhs.matches(json) || rhs.matches(json)
  }

  case class Or(lhs: JsonMatcher, rhs: JsonMatcher) extends JsonMatcher {
    override def matches(json: Json): Boolean = lhs.matches(json) || rhs.matches(json)
  }

  case class And(lhs: JsonMatcher, rhs: JsonMatcher) extends JsonMatcher {
    override def matches(json: Json): Boolean = lhs.matches(json) && rhs.matches(json)
  }

  implicit object JsonMatcherJson extends Encoder[JsonMatcher] with Decoder[JsonMatcher] {
    override def apply(jsonMatcher: JsonMatcher): Json = {
      jsonMatcher match {
        case MatchAll => Json.fromString("MatchAll")
        case other => sys.error(s"$other not implemented")
      }
    }

    override def apply(c: HCursor): Result[JsonMatcher] = {
      val v = c.value
      val matchAllOpt: Option[JsonMatcher] = c.value.asString collect {
        case "MatchAll" => matchAll
      }

      matchAllOpt.
        toRight(DecodingFailure(s"Expected a json matcher", c.history))
    }
  }

}
