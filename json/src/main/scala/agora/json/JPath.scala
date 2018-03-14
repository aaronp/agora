package agora.json

import agora.json.JPath.select
import io.circe.Decoder.Result
import io.circe._

/**
  * Represents a json path (like an xpath is to xml)
  *
  * @param path the segments of the path
  */
case class JPath(path: List[JPart]) {

  import io.circe._
  import io.circe.generic.auto._
  import io.circe.syntax._

  def ++(other: JPath) = copy(path = path ++ other.path)

  def +:[T](other: T)(implicit ev: T => JPart) = copy(path = ev(other) +: path)

  def :+[T](other: T)(implicit ev: T => JPart) = copy(path = path :+ ev(other))

  /** @return the json representation of this JPath
    */
  def json: Json = {
    new EncoderOps(this).asJson
  }

  /** selects the value represented by this JPath from the given json
    *
    * e.g. the JPath x.y.z would select the json value of 'z', if it exists
    *
    * @param json the json from which the value should be extracted
    * @return the value if it exists
    */
  def apply(json: Json): Option[Json] = selectValue(json)

  /** @param json
    * @return the json value designated by this path. If you want to include
    */
  def selectValue(json: Json): Option[Json] = JPath.select(path, json.hcursor).focus

  /** like selectValue, but the json include the path
    *
    * e.g. given the json
    * {{{
    *   {
    *     "x" : { "y" : { "z" : 123, "z1" : 456 } }
    *   }
    * }}}
    *
    * 'select' would returns the json number '123'.
    *
    * 'selectJson' would return
    *
    * {{{
    *   {
    *     "x" : { "y" : { "z" : 123 } }
    *   }
    * }}}
    *
    * select only json which contains this path
    */
  def selectJson(json: Json): Option[Json] = {
    selectValue(json).map { value =>
      JPath.selectJson(path, value)
    }
  }

  /**
    * The json w/ the value appended, if the path existed in the target json
    *
    * @param json  the target json to which the value will be appended at this path
    * @param value the value to append
    * @tparam T the value type which can be encoded to json
    * @return the updated json if the path existed in the target json
    */
  def appendTo[T: Encoder](json: Json, value: T): Option[Json] = {
    val opt = JPath.select(path, json.hcursor).withFocus { json =>
      deepMergeWithArrayConcat(json, implicitly[Encoder[T]].apply(value))
    }
    opt.top
  }

  /**
    * removes the entry from the given json. any 'array contains' filters
    * are negated, as the intention is to _remove_ matching values, not
    * select (match) them.
    *
    * @param json the target json to which the value will be removed
    * @return the updated json if the path existed in the target json
    */
  def removeFrom(json: Json): Option[Json] = select(path, json.hcursor).delete.top

  def asMatcher(filter: JPredicate = JPredicate.matchAll) = JPredicate(this, filter)
}

object JPath {

  val root = JPath(Nil)

  import JPredicate.implicits._

  def apply(first: JPart, parts: JPart*): JPath = JPath(first :: parts.toList)

  def apply(only: String): JPath = forParts(only.split("\\.", -1).map(_.trim).filterNot(_.isEmpty).toList)

  def apply(first: String, second: String, parts: String*): JPath = {
    forParts(first :: second :: parts.toList)
  }

  implicit object JsonFormat extends Encoder[JPath] with Decoder[JPath] {
    override def apply(a: JPath): Json = {
      import io.circe.generic.auto._
      import io.circe.syntax._
      a.path.asJson
    }

    override def apply(c: HCursor): Result[JPath] = {
      val parts: Result[List[JPart]] = c.as[List[JPart]]
      parts.right.map(JPath.apply)
    }
  }

  /** @param typeByPath as produced by a [[TypeNode]]
    * @return a path for a TypeByPath as produced e.g. from a [[TypeNode]]
    */
  def forTypesByPath(typeByPath: TypeByPath): JPath = forParts(typeByPath._1)

  def forParts(first: String, theRest: String*): JPath = forParts(first :: theRest.toList)

  def forParts(parts: List[String]): JPath =
    JPath(parts.map {
      case IntR(i)      => JPos(i.toInt)
      case ValueR(f, v) => f === Json.fromString(v)
      case name         => JField(name)
    })

  /**
    * Unmarshalls a JPath from the JPath's json representation
    * @param jsonString
    * @return the unmarshalled JPath
    */
  def fromJson(jsonString: String): JPath = {

    import io.circe.generic.auto._
    import io.circe.parser._
    decode[JPath](jsonString) match {
      case Left(err)    => throw err
      case Right(jpath) => jpath
    }
  }

  private implicit class RichCursor(val a: ACursor) extends AnyVal {
    def withHCursor(f: HCursor => ACursor): ACursor = a.success.fold(a)(f)
  }

  private[json] def select(parts: List[JPart], cursor: HCursor): ACursor = {
    parts match {
      case Nil                   => cursor
      case JField(field) :: tail => cursor.downField(field).withHCursor(select(tail, _))
      case JPos(pos) :: tail =>
        cursor.downArray.withHCursor { ac =>
          ac.rightN(pos).withHCursor(select(tail, _))
        }
      case JArrayFind(predicate) :: tail =>
        cursor.downArray.withHCursor { c =>
          val found = c.find(predicate.matches)
          found.withHCursor(select(tail, _))
        }
      case JFilter(field, predicate) :: tail =>
        cursor.downField(field).withHCursor { c =>
          if (c.focus.exists(predicate.matches)) {
            select(tail, c)
          } else {
            new FailedCursor(c, CursorOp.DownField(field))
          }
        }
    }
  }

  // used by selectJson to extract JParts which represent objects
  private object ObjectPart {
    def unapply(part: JPart): Option[String] = {
      part match {
        case JField(field)     => Option(field)
        case JFilter(field, _) => Option(field)
        case _                 => None
      }
    }
  }

  // used by selectJson to extract JParts which represent arrays
  private object ArrayPart {
    def unapply(part: JPart): Boolean = {
      part match {
        case JPos(_)       => true
        case JArrayFind(_) => true
        case _             => false
      }
    }
  }

  def selectJson(parts: List[JPart], value: Json): Json = {
    parts match {
      case Nil                       => value
      case ObjectPart(field) :: tail => Json.obj(field -> selectJson(tail, value))
      case ArrayPart() :: tail       => Json.arr(selectJson(tail, value))
    }
  }

  private val IntR   = "(\\d+)".r
  private val ValueR = "(.*)=(.*)".r
}
