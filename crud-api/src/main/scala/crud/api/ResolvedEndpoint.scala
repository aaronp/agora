package crud.api

case class ResolvedEndpoint(location: HostPort, uri: String) {
  def host = location.host

  def port = location.port

  def hostPort = location.hostPort
}

object ResolvedEndpoint {
  def apply(port : Int, uri : String): ResolvedEndpoint = ResolvedEndpoint(HostPort.localhost(port), uri)
}