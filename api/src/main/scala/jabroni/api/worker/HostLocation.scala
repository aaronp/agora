package jabroni.api.worker

import java.net.InetAddress

import io.circe.optics.JsonPath
import io.circe.{Encoder, Json}
import jabroni.api._
import jabroni.api.json.JsonAppendable

import scala.util.Properties

case class HostLocation(host: String, port: Int) {
  def asURL = s"http://$host:$port"
}

object HostLocation {
  def apply(port: Int): HostLocation = {
    val host = InetAddress.getLocalHost.getHostName
    HostLocation(host, port)
  }
}
