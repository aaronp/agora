package agora.api.worker

case class HostLocation(host: String, port: Int) {
  def asURL = s"http://$host:$port"
}

object HostLocation {

  // doing this is very slow, but obviously all workers can just say 'localhost' to a central exchange
  //java.net.InetAddress.getLocalHost.getHostName
  private lazy val host = "localhost"

  def localhost(port: Int): HostLocation = HostLocation(host, port)
}
