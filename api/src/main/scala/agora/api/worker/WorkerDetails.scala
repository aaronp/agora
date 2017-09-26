package agora.api.worker

import agora.api.User
import agora.api.json.{JsonAppendable, JsonDelta, _}
import io.circe.Decoder.Result
import io.circe._
import io.circe.optics.JsonPath

import scala.util.Properties

/**
  * WorkerDetails is a wrapper for the information you fancy exposing for workers requesting work.
  *
  * They contain some json values (which can be appended to/updated) that jobs can match against.
  *
  * These values should also expose sufficient information for calling clients to do their stuff.
  *
  * For example, with REST services, workers will need to contain a 'location' and 'path' entry so clients submitting
  * jobs will know where to redirect their jobs to.
  *
  * @param aboutMe an opaque block of Json exposed by a worker. It must however include a 'path' element to describe the relative URL to send work requests to
  */
case class WorkerDetails(override val aboutMe: Json) extends JsonAppendable {

  /**
    * Append the location to the 'aboutMe' json
    *
    * @param location the location to append
    * @return an updated worker location
    */
  def withLocation(location: HostLocation): WorkerDetails = {
    import io.circe.generic.auto._
    append("location", location)
  }

  override def toString = aboutMe.spaces2

  /**
    * Appends the data to the worker
    *
    * @param data some data which can be encoded into json
    * @tparam T the data type
    * @return an updated worker details
    */
  def append[T: Encoder](name: String, data: T): WorkerDetails = {
    val json =
      implicitly[Encoder[T]].apply(data)
    append(Json.obj(name -> json))
  }

  /**
    * An 'append' which appends the data as <className> -> <encoded T as json>
    * @param data
    * @tparam T
    * @return an updated work details with the given data appended
    */
  def +[T: Encoder](data: T): WorkerDetails = {
    val name = WorkerDetails.asName(data.getClass)
    append(name, data)
  }

  def append(json: Json): WorkerDetails = copy(deepMergeWithArrayConcat(aboutMe, json))

  /**
    * Appends the delta
    *
    * @param delta the delta to apply
    * @return either an updated WorkerDetails or None if the delta had no effect
    */
  def update(delta: JsonDelta) =
    delta.update(aboutMe).map { updated =>
      copy(updated)
    }

  import WorkerDetails._

  def withData[T: Encoder](data: T, name: String = null) =
    copy(aboutMe = mergeJson(data, name))

  def get[T: Decoder](name: String): Result[T] = {
    val cursor: ACursor =
      aboutMe.hcursor.downField(name)
    cursor match {
      case h: HCursor =>
        val decoder: Decoder[T] =
          implicitly[Decoder[T]]
        decoder(h)
      case other =>
        Left(DecodingFailure(s"Couldn't unmarshal '$name' from $aboutMe: $other", cursor.history))
    }
  }

  def withPath(path: String) = {
    append(Json.obj("path" -> Json.fromString(path)))
  }

  def withSubscriptionKey(key: SubscriptionKey) =
    append("id", key)

  def subscriptionKey =
    keyPath
      .getOption(aboutMe)
      .map(_.trim)
      .filterNot(_.isEmpty)

  private def locationOpt: Option[HostLocation] = {
    for {
      host <- hostPath.getOption(aboutMe)
      port <- portPath
        .getOption(aboutMe)
        .orElse(
          portStringPath
            .getOption(aboutMe)
            .map(_.toInt))
    } yield HostLocation(host, port)
  }

  def location: HostLocation =
    locationOpt.getOrElse {
      sys.error(s"invalid json: 'location' not set: ${aboutMe}")
    }

  def name = namePath.getOption(aboutMe)

  /** @return the fully qualified worker URL
    */
  def url: Option[String] =
    pathOpt.map(p => s"${location.asURL}/$p")

  /** @return the relative path for the endpoint under which this worker will receive work
    */
  def pathOpt: Option[String] =
    pathPath
      .getOption(aboutMe)
      .map(_.trim)
      .filterNot(_.isEmpty)

  def path: String =
    pathOpt.getOrElse(sys.error(s"No 'path' set on $this"))

  def runUser: User =
    runUserPath
      .getOption(aboutMe)
      .getOrElse {
        sys.error(s"invalid json: 'runUser' not set: ${aboutMe}")
      }
}

object WorkerDetails {

  val empty = WorkerDetails(Json.Null)

  import JsonPath._
  import io.circe.generic.auto._
  import io.circe.syntax._

  val locationPath = root.location
  val hostPath =
    locationPath.host.string
  val portPath = locationPath.port.int
  val portStringPath =
    locationPath.port.string
  val pathPath    = root.path.string
  val namePath    = root.name.string
  val keyPath     = root.id.string
  val runUserPath = root.runUser.string

  /**
    * TODO - move the typesafe config as part of the jvn dependency so that we can pull the worker config
    * in here. As it currently stands, we have to duplicate these fields in this weird way instead of
    * using the typical init code produced by the workerconfig
    */
  private case class DefaultDetails(runUser: String, location: HostLocation, name: String, id: String, path: String)

  def apply(location: HostLocation,
            path: String = "handler",
            name: String = "worker",
            id: SubscriptionKey = "",
            runUser: String = Properties.userName): WorkerDetails = {
    val json =
      DefaultDetails(runUser, location, name, id, path).asJson
    WorkerDetails(json)
  }

  def asName(c1ass: Class[_]): String = {
    val name = c1ass.getSimpleName
      .replaceAllLiterally("$", "")
    name.headOption.fold("")(_.toLower +: name.tail)
  }
}
