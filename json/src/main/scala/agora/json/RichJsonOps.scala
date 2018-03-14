package agora.json

import io.circe.Json

case class RichJsonOps(val json: Json) extends AnyVal {

  /**
    * @param path    the first jpath to filter on
    * @param theRest the rest of the paths to filter
    * @return json which only contains values which match the supplied paths
    */
  def onlyWith(path: JPath, theRest: JPath*): Json = onlyWith(path :: theRest.toList)

  /**
    * @param paths paths to include
    * @return json which only contains values which match the supplied paths
    */
  def onlyWith(paths: Iterable[JPath]): Json = {
    val parts: Iterable[Json] = paths.flatMap(_.selectJson(json))
    if (parts.isEmpty) {
      Json.Null
    } else {
      parts.reduce(deepMergeWithArrayConcat)
    }
  }

}

object RichJsonOps {

  trait LowPriorityJsonOpsImplicits {
    implicit def asRichJson(json: Json) = RichJsonOps(json)
  }

}
