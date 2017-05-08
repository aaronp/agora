package jabroni.api.worker

import io.circe.optics.JsonPath
import io.circe.{Encoder, Json}
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

  def append(data: Json): WorkerDetails = copy(aboutMe.deepMerge(data))

  import WorkerDetails._

  def withData[T: Encoder](data: T, name: String = null) = copy(aboutMe = mergeJson(data, name))

  def withPath(path: String) = {
    append(Json.obj("path" -> Json.fromString(path)))
  }

  def withSubscriptionKey(key: SubscriptionKey) = append("id", key)

  def subscriptionKey = keyPath.getOption(aboutMe).map(_.trim).filterNot(_.isEmpty)

  private def locationOpt: Option[HostLocation] = {
    for {
      host <- hostPath.getOption(aboutMe)
      port <- portPath.getOption(aboutMe).orElse(portStringPath.getOption(aboutMe).map(_.toInt))
    } yield HostLocation(host, port)
  }

  def location: HostLocation = locationOpt.getOrElse {
    sys.error(s"invalid json: 'location' not set: ${aboutMe}")
  }

  def url = path.map(p => s"${location.asURL}/$p")

  def name = namePath.getOption(aboutMe)

  def path: Option[String] = pathPath.getOption(aboutMe).map(_.trim).filterNot(_.isEmpty)

  def runUser: User = runUserPath.getOption(aboutMe).getOrElse {
    sys.error(s"invalid json: 'runUser' not set: ${aboutMe}")
  }
}

object WorkerDetails {

  import JsonPath._
  import io.circe.generic.auto._
  import io.circe.syntax._

  val locationPath = root.location
  val hostPath = locationPath.host.string
  val portPath = locationPath.port.int
  val portStringPath = locationPath.port.string
  val pathPath = root.path.string
  val namePath = root.name.string
  val keyPath = root.id.string
  val runUserPath = root.runUser.string

  val defaultPort = 1234

  /**
    * TODO - move the typesafe config as part of the jvn dependency so that we can pull the worker config
    * in here. As it currently stands, we have to duplicate these fields in this weird way instead of
    * using the typical init cdde produced by the workerconfig
    */
  private case class DefaultDetails(runUser: String, location: HostLocation, name: String, id: String, path: String)

  def apply(name: String = "worker",
            id: SubscriptionKey = "",
            runUser: String = Properties.userName,
            path: String = "handler",
            location: HostLocation = HostLocation(defaultPort)): WorkerDetails = {
    val json = DefaultDetails(runUser, location, name, id, path).asJson
    WorkerDetails(json)
  }

  def asName(c1ass: Class[_]): String = {
    val name = c1ass.getSimpleName.replaceAllLiterally("$", "")
    name.headOption.fold("")(_.toLower +: name.tail)
  }
}