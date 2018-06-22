package agora.api.config

case class HostLocation(host: String, port: Int, secure: Boolean = false) {
  def asHostPort = s"$host:$port"

  /**
    * @return 'localhost:port' if the host is set to 0.0.0.0 then use, this instance otherwise
    */
  def resolveLocalhost = host match {
    case "0.0.0.0" => HostLocation("localhost", port)
    case _         => this
  }

  def asURL = s"http://$asHostPort"

  def asWebsocketURL = {
    if (secure) {
      s"wss://${asHostPort}"
    } else {
      s"ws://${asHostPort}"
    }
  }
}

object HostLocation {

  // doing this is very slow, but obviously all workers can just say 'localhost' to a central exchange
  //java.net.InetAddress.getLocalHost.getHostName
  private lazy val host = "localhost"

  def localhost(port: Int): HostLocation = HostLocation(host, port)

  private val HostPort = "(.*):(\\d+)".r
  def unapply(id: String): Option[HostLocation] = {
    id match {
      case HostPort(h, p) => Option(HostLocation(h, p.toInt))
      case _              => None
    }
  }
}
