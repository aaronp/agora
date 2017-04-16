package jabroni.api.json

import io.circe._
import io.circe.optics.{JsonPath, JsonTraversalPath}

object JPath {

  import JPredicate.implicits._

  def apply(parts: String*): JPath = JPath(parts.map {
    case IntR(i) => JPos(i.toInt)
    case ValueR(f, v) =>

      val jf: JFilter = f === Json.fromString(v)
      jf
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

      //        next.focus.flatMap { json =>
      //          select(tail,)
      //        }
      //        next.focus.filter {
      //          case json.asString
      //          case json.asBoolean
      //          case json.asNumber
      //          case json.asArray
      //          case json.asObject
      //        }
      //      case head :: tail => ???
    }
  }

  private val IntR = "(\\d+)".r
  private val ValueR = "(.*)=(.*)".r
}


case class JPath(parts: List[JPart]) {

  import JPath._
  import io.circe._
  import io.circe.generic.auto._
  import io.circe.optics._
  import io.circe.syntax._

  def json: Json = {
    new EncoderOps(this).asJson
  }


  private def asTraversal(json: Json, path: JsonTraversalPath, remaining: List[String]): Either[JsonTraversalPath, JsonPath] = {
    remaining match {
      case Nil => Left(path)
      case "each" :: tail => asTraversal(json, path.each, tail)
      case head :: tail => asTraversal(json, path.selectDynamic(head), tail)
    }
  }

  //  private def asPath(json: Json, path: JsonPath, remaining: List[String]): Either[JsonTraversalPath, JsonPath] = {
  //    remaining match {
  //      case Nil => Right(path)
  //      case IntR(i) :: tail =>
  //        val next = path.index(i.toInt)
  //        val opt: Option[Json] = next.json.getOption(json)
  //        asPath(json, path.apply(i.toInt), tail)
  //      case head :: tail =>
  //
  //        val next = path.selectDynamic(head)
  //        val opt: Option[Json] = next.json.getOption(json)
  //        val isArray = opt.exists(_.isArray)
  //        if (isArray) {
  //          asTraversal(json, next.each, tail)
  //        } else {
  //          asPath(opt.getOr)
  //        }
  //
  //      case "each" :: tail =>
  //        path.at("theRest").getOption(json)
  //        println("each")
  //        asTraversal(json, path.each, tail)
  //      case head :: tail => asPath(json, path.selectDynamic(head), tail)
  //    }
  //  }
}
