package jabroni.api

object JPath {
  def apply(parts: Symbol*): JPath = JPath(parts.map(_.name).toList)

  def fromJson(jsonString: String) = {

    import io.circe.generic.auto._
    import io.circe.parser._
    decode[JPath](jsonString) match {
      case Left(err) => throw err
      case Right(jpath) => jpath
    }
  }

  private val IntR = "(\\d+)".r
}

case class JPath(parts: List[String]) {

  import io.circe._
  import io.circe.generic.auto._
  import io.circe.optics._
  import io.circe.syntax._
  import JPath._

  def json: Json = {
    new EncoderOps(this).asJson
  }

  final def string(json: Json) = path match {
    case Left(t) => t.string.getAll(json)
    case Right(p) => p.string.getOption(json).toList
  }

  final def int(json: Json) = path match {
    case Left(t) => t.int.getAll(json)
    case Right(p) => p.int.getOption(json).toList
  }

  final def long(json: Json) = path match {
    case Left(t) => t.long.getAll(json)
    case Right(p) => p.long.getOption(json).toList
  }

  private lazy val path = asPath(JsonPath.root, parts)


  private def asTraversal(path: JsonTraversalPath, remaining: List[String]): Either[JsonTraversalPath, JsonPath] = {
    remaining match {
      case Nil => Left(path)
      case "each" :: tail => asTraversal(path.each, tail)
      case head :: tail => asTraversal(path.selectDynamic(head), tail)
    }
  }

  private def asPath(path: JsonPath, remaining: List[String]): Either[JsonTraversalPath, JsonPath] = {
    remaining match {
      case Nil => Right(path)
      case IntR(i) :: tail => asPath(path.apply(i.toInt), tail)
      case "each" :: tail =>
        println("each")
        asTraversal(path.each, tail)
      case head :: tail => asPath(path.selectDynamic(head), tail)
    }
  }
}
