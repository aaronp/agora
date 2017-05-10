package jabroni.api.worker

import io.circe.{Encoder, Json}
import io.circe.optics.JsonPath
import io.circe.optics.JsonPath.root
import jabroni.api.User
import jabroni.api.json.JsonAppendable

import scala.util.Properties


/**
  * The 'aboutMe' should also contain the location/user
  *
  * @param aboutMe
  */
case class WorkerDetails(override val aboutMe: Json) extends JsonAppendable {

  override def toString = aboutMe.spaces2

  def +[T: Encoder](data: T): WorkerDetails = append(WorkerDetails.asName(data.getClass), data)

  def +[T: Encoder](name: String, data: T): WorkerDetails = append(name, implicitly[Encoder[T]].apply(data))

  def append[T: Encoder](name: String, data: T): WorkerDetails = append(name, implicitly[Encoder[T]].apply(data))

  def append(name: String, data: Json): WorkerDetails = append(Json.obj(name -> data))

  def append(data: Json): WorkerDetails = copy(data.deepMerge(aboutMe))

  import WorkerDetails._

  def withData[T: Encoder](data: T, name: String = null) = copy(aboutMe = mergeJson(data, name))

  def withPath(path : String) = append("path", path)

  private def locationOpt: Option[HostLocation] = {
    for {
      host <- hostPath.getOption(aboutMe)
      port <- portPath.getOption(aboutMe)
    } yield HostLocation(host, port)
  }

  def location: HostLocation = locationOpt.getOrElse {
    sys.error(s"invalid json: 'location' not set: ${aboutMe}")
  }

  def url = path.map(p => s"${location.asURL}/$p")

  def name = namePath.getOption(aboutMe)

  private def computedId = {
    s"${location.host}:${location.port}/${path.getOrElse("no-path")}/${name.getOrElse("")}"
  }
  lazy val id: SubscriptionKey = {
    val opt = idPath.getOption(aboutMe).map(_.trim).filterNot(_.isEmpty)
    opt.getOrElse(computedId)
  }

  def path: Option[String] = pathPath.getOption(aboutMe).map(_.trim).filterNot(_.isEmpty)

  def runUser: User = runUserPath.getOption(aboutMe).getOrElse {
    sys.error(s"invalid json: 'runUser' not set: ${aboutMe}")
  }
}

object WorkerDetails {

  import io.circe.generic.auto._
  import io.circe.syntax._
  import JsonPath._
  val locationPath = root.location
  val hostPath = locationPath.host.string
  val portPath = locationPath.port.int
  val pathPath = root.path.string
  val namePath = root.name.string
  val idPath = root.id.string
  val runUserPath = root.runUser.string

  val defaultPort = 1234

  private case class DefaultDetails(runUser: String, location: HostLocation, name: String, id: String)

  def apply(name: String = "worker",
            id: SubscriptionKey = "",
            runUser: String = Properties.userName,
            location: HostLocation = HostLocation(defaultPort)): WorkerDetails = {
    val json = DefaultDetails(runUser, location, name, id).asJson
    WorkerDetails(json)
  }

  def asName(c1ass: Class[_]): String = {
    val name = c1ass.getSimpleName.replaceAllLiterally("$", "")
    name.headOption.fold("")(_.toLower +: name.tail)
  }
}