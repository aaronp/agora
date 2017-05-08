package jabroni.api.json

import io.circe._

object JPath {

  import JPredicate.implicits._

  def apply(first : JPart, parts: JPart*): JPath = JPath(first :: parts.toList)

  def apply(first : String, parts: String*): JPath = JPath((first +: parts).map {
    case IntR(i) => JPos(i.toInt)
    case ValueR(f, v) => f === Json.fromString(v)
    case name => JField(name)
  }.toList)

  def fromJson(jsonString: String): JPath = {

    import io.circe.generic.auto._
    import io.circe.parser._
    decode[JPath](jsonString) match {
      case Left(err) => throw err
      case Right(jpath) => jpath
    }
  }


  implicit class RichCursor(val a: ACursor) extends AnyVal {
    def asHCursor: Option[HCursor] = Option(a) collect {
      case h: HCursor => h
    }

    def withHCursor(f: HCursor => ACursor): ACursor = asHCursor.fold(a)(f)
  }

  def select(parts: List[JPart], cursor: HCursor): ACursor = {
    parts match {
      case Nil => cursor
      case JField(field) :: tail => cursor.downField(field).withHCursor(select(tail, _))
      case JPos(pos) :: tail =>
        cursor.downArray.withHCursor { ac =>
          ac.rightN(pos).withHCursor(select(tail, _))
        }
      case JFilter(field, predicate) :: tail =>
        val a = cursor.downField(field)
        a.withHCursor { c =>
          if (c.focus.exists(predicate.matches)) {
            select(tail, c)
          } else {
            //            Left(DecodingFailure(s"$c didn't match $predicate", c.history))
            new FailedCursor(c, CursorOp.DownField(field))
          }
        }
    }
  }

  private val IntR = "(\\d+)".r
  private val ValueR = "(.*)=(.*)".r
}


case class JPath(parts: List[JPart]) {

  import io.circe._
  import io.circe.generic.auto._
  import io.circe.syntax._

  def ++(other : JPath) = copy(parts = parts ++ other.parts)
  def +:(other : JPart) = copy(parts = other +: parts)

  def json: Json = {
    new EncoderOps(this).asJson
  }

  def apply(json : Json): Option[Json] = {
    JPath.select(parts, json.hcursor).focus
  }

  def asMatcher = JMatcher(this)
}
