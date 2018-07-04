package streaming.api

import streaming.api.EndpointCoords.Part

case class EndpointCoords(location: HostPort, uri: List[Part]) {
  def host = location.host

  def port = location.port

  def hostPort = location.hostPort
}

object EndpointCoords {

  /**
    * represents part of a URI path
    */
  sealed trait Part

  object Part {
    val ParamR = ":(.*)".r

    private def asPart(str: String): Part = {
      str match {
        case ParamR(n) => ParamPart(n)
        case n => StringPart(n)
      }
    }

    def apply(str: String): List[Part] = str.split("/", -1).map(asPart).toList
  }

  case class StringPart(part: String) extends Part
  case class ParamPart(name: String) extends Part

  def apply(port: Int, uri: String): EndpointCoords = EndpointCoords(HostPort.localhost(port), Part(uri))
  def apply(port: Int, parts: Part*): EndpointCoords = EndpointCoords(HostPort.localhost(port), parts.toList)
}
