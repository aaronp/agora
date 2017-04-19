package jabroni.api.worker

import java.net.{InetAddress, URI}

import io.circe.Json
import io.circe.optics.JsonPath
import jabroni.api.User

import scala.util.Properties

/**
  * The 'aboutMe' should also contain the location/user
  *
  * @param aboutMe
  */
case class WorkerDetails(aboutMe: Json) {

  import WorkerDetails._

  private def locationOpt = locationPath.getOption(aboutMe).map(URI.create)

  def location: URI = locationOpt.getOrElse {
    sys.error(s"invalid json: 'location' not set: ${aboutMe}")
  }

  def runUser: User = runUserPath.getOption(aboutMe).getOrElse {
    sys.error(s"invalid json: 'runUser' not set: ${aboutMe}")
  }
}

object WorkerDetails {

  import io.circe.generic._
  import io.circe.generic.auto._
  import io.circe.parser._
  import io.circe.export._
  import io.circe.syntax._
  import io.circe._

  val locationPath = JsonPath.root.location.string
  val runUserPath = JsonPath.root.runUser.string

  private case class DefaultDetails(runUser: String, location: String)

  def localUri = {
    val localhost: InetAddress = InetAddress.getLocalHost
    localhost.
  }

  def apply(runUser: String = Properties.userName, location: URI): WorkerDetails = {
    val details = DefaultDetails(runUser, location.toASCIIString)
    val json = details.asJson
    WorkerDetails(json)
  }
}