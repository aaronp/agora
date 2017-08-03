package agora.api.worker

case class HostLocation(host: String, port: Int) {
  def asURL = s"http://$host:$port"
  if (port == 1234) {
    println(port)
  }
}

object HostLocation {

  private lazy val host = "localhost" //java.net.InetAddress.getLocalHost.getHostName

  def apply(port: Int): HostLocation = HostLocation(host, port)
}
