package streaming.rest

import streaming.rest.HttpMethod._
import streaming.rest.WebURI._

/** Meant to be used when declaring routes.
  *
  * The various streaming impls should be able to improve on this - it's just meant to be a simple
  * way to declare some basic paths.
  *
  * Some parts of the uri may be placeholders -- whether those placeholders are strings, ints, UUIDs, etc, in the
  * end, they all need to resolve to strings.
  *
  * @param uri the uri parts
  */
case class WebURI(method: HttpMethod, uri: List[Part]) {

  override def toString = s"$method ${uri.mkString("/")}"

  /**
    * Used to resolve the route uri to a string
    * @param params
    * @return a Left of an error or a Right containing the uri parts
    */
  def resolve(params: Map[String, String] = Map.empty): Either[String, List[String]] = {
    import cats.syntax.either._

    val either: Either[String, List[String]] = uri.foldLeft(List[String]().asRight[String]) {
      case (Right(list), ParamPart(key)) =>
        params.get(key).map(_ :: list).toRight {
          s"The supplied parameters doesn't contain an entry for '$key"
        }
      case (Right(list), ConstPart(key)) => Right(key :: list)
    }
    either.map(_.reverse)
  }
}

object WebURI {

  /**
    * represents part of a URI path
    */
  sealed trait Part

  object Part {
    val ParamR = ":(.*)".r

    private def asPart(str: String): Part = {
      str match {
        case ParamR(n) => ParamPart(n)
        case n         => ConstPart(n)
      }
    }

    def apply(str: String): List[Part] = str.split("/", -1).map(_.trim).filterNot(_.isEmpty).map(asPart).toList
  }

  case class ConstPart(part: String) extends Part {
    override def toString = part
  }

  case class ParamPart(name: String) extends Part {
    override def toString = name
  }

  def get(uri: String): WebURI     = WebURI(GET, Part(uri))
  def delete(uri: String): WebURI  = WebURI(DELETE, Part(uri))
  def put(uri: String): WebURI     = WebURI(PUT, Part(uri))
  def post(uri: String): WebURI    = WebURI(POST, Part(uri))
  def head(uri: String): WebURI    = WebURI(HEAD, Part(uri))
  def options(uri: String): WebURI = WebURI(OPTIONS, Part(uri))

  def apply(method: HttpMethod, uri: String): WebURI = new WebURI(method, Part(uri))

  def apply(method: HttpMethod, parts: Part*): WebURI = new WebURI(method, parts.toList)
}
