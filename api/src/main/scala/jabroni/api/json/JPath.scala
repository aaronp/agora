package jabroni.api.json

import io.circe.Json
import io.circe.optics.{JsonPath, JsonTraversalPath}

object JPath {
  def apply(parts: Symbol*): JPath = apply(parts.map(_.name): _*)

  def apply(parts: String*): JPath = JPath(parts.map {
    case IntR(i) => JPos(i.toInt)
    case ValueR(f, v) => JFilterValue(f, v)
    case name => JField(name)
  }.toList)

  def fromJson(jsonString: String) = {

    import io.circe.generic.auto._
    import io.circe.parser._
    decode[JPath](jsonString) match {
      case Left(err) => throw err
      case Right(jpath) => jpath
    }
  }

  private val IntR = "(\\d+)".r
  private val ValueR = "(.*)=(.*)".r
}

sealed trait JPart {

  def advance(json: Json, path: JsonTraversalPath): JsonTraversalPath

  def advance(json: Json, path: JsonPath): Either[JsonTraversalPath, JsonPath]
}

case class JField(name: String) extends JPart {

  override def advance(json: Json, path: JsonTraversalPath): JsonTraversalPath = {
    val next = path.selectDynamic(name)

  }

  override def advance(json: Json, path: JsonPath) = {
    val next = path.selectDynamic(name)
    val opt: Option[Json] = next.json.getOption(json)
    val isArray = opt.exists(_.isArray)
    if (isArray) {
      Left(path.each)
    } else {
      Right(next)
    }
  }
}

case class JPos(i: Int) extends JPart {
  override def advance(json: Json, path: JsonTraversalPath) = ???

  override def advance(json: Json, path: JsonPath) = {
    val next = path.index(i)
    val opt: Option[Json] = next.json.getOption(json)
    opt -> Right(next)
  }
}

case class JFilterValue(field: String, value: String) extends JPart {
  override def advance(json: Json, path: JsonTraversalPath): (Option[Json], JsonTraversalPath) = ???

  override def advance(json: Json, path: JsonPath): (Option[Json], Either[JsonTraversalPath, JsonPath]) = ???
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


  def path(json : Json) = {
    val e : Either[JsonTraversalPath, JsonPath] = Right(JsonPath.root)
    parts.foldLeft(e -> json) {
      case ((Left(path), j), part) => Left(part.advance(j, path))
      case ((Right(path), j), part) => part.adva
    }
  }

  final def string(json: Json) = asPath(json, JsonPath.root, parts) match {
    case Left(t) => t.string.getAll(json)
    case Right(p) => p.string.getOption(json).toList
  }
//
//  final def int(json: Json) = asPath(json, JsonPath.root, parts) match {
//    case Left(t) => t.int.getAll(json)
//    case Right(p) => p.int.getOption(json).toList
//  }
//
//  final def long(json: Json) = asPath(json, JsonPath.root, parts) match {
//    case Left(t) => t.long.getAll(json)
//    case Right(p) => p.long.getOption(json).toList
//  }

  //  private lazy val path = asPath(JsonPath.root, parts)


  private def asTraversal(json: Json, path: JsonTraversalPath, remaining: List[String]): Either[JsonTraversalPath, JsonPath] = {
    remaining match {
      case Nil => Left(path)
      case "each" :: tail => asTraversal(json, path.each, tail)
      case head :: tail => asTraversal(json, path.selectDynamic(head), tail)
    }
  }

  private def asPath(json: Json, path: JsonPath, remaining: List[String]): Either[JsonTraversalPath, JsonPath] = {
    remaining match {
      case Nil => Right(path)
      case IntR(i) :: tail =>
        val next = path.index(i.toInt)
        val opt: Option[Json] = next.json.getOption(json)
        asPath(json, path.apply(i.toInt), tail)
      case head :: tail =>

        val next = path.selectDynamic(head)
        val opt: Option[Json] = next.json.getOption(json)
        val isArray = opt.exists(_.isArray)
        if (isArray) {
          asTraversal(json, next.each, tail)
        } else {
          asPath(opt.getOr)
        }

      case "each" :: tail =>
        path.at("theRest").getOption(json)
        println("each")
        asTraversal(json, path.each, tail)
      case head :: tail => asPath(json, path.selectDynamic(head), tail)
    }
  }
}
