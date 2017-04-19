package jabroni.api.worker

import java.net.{InetAddress, URI}

import io.circe.Json
import io.circe.optics.JsonPath
import jabroni.api.User

import scala.util.Properties

case class HostLocation(host: String, port: Int)

object HostLocation {
  def apply(port: Int = 8080): HostLocation = {
    val host = InetAddress.getLocalHost.getHostName
    HostLocation(host, port)
  }
}

/**
  * The 'aboutMe' should also contain the location/user
  *
  * @param aboutMe
  */
case class WorkerDetails(aboutMe: Json) {

  import WorkerDetails._

  private def locationOpt: Option[HostLocation] = {
    for {
      host <- hostPath.getOption(aboutMe)
      port <- portPath.getOption(aboutMe)
    } yield HostLocation(host, port)
  }

  def location: HostLocation = locationOpt.getOrElse {
    sys.error(s"invalid json: 'location' not set: ${aboutMe}")
  }

  def runUser: User = runUserPath.getOption(aboutMe).getOrElse {
    sys.error(s"invalid json: 'runUser' not set: ${aboutMe}")
  }
}

object WorkerDetails {

  import io.circe.generic.auto._
  import io.circe.syntax._

  val locationPath = JsonPath.root.location
  val hostPath = locationPath.host.string
  val portPath = locationPath.port.int
  val runUserPath = JsonPath.root.runUser.string

  private case class DefaultDetails(runUser: String, location: HostLocation)

  def apply(runUser: String, location: HostLocation): WorkerDetails = {
    val details = DefaultDetails(runUser, location)
    val json = details.asJson
    WorkerDetails(json)
  }
}