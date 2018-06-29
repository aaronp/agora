package streaming.api

case class EndpointCoords(location: HostPort, uri: String) {
  def host = location.host

  def port = location.port

  def hostPort = location.hostPort
}

object EndpointCoords {
  def apply(port: Int, uri: String): EndpointCoords = EndpointCoords(HostPort.localhost(port), uri)
}
