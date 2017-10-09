package agora.api.json

import io.circe.{Encoder, Json}

/**
  * Represents changes to some json
  *
  * @param remove the jpaths to remove
  * @param append the json to append
  */
case class JsonDelta(remove: List[JPath] = Nil, append: Json = Json.Null) {

  def isEmpty = remove.isEmpty && append == Json.Null

  /**
    * Apply this to the input json
    *
    * @param original the input json
    * @return a Some of the updated json, None if this had no effect
    */
  def update(original: Json) = {
    if (isEmpty) {
      None
    } else doUpdate(original)
  }

  private def doUpdate(original: Json) = {
    val deletes = remove.foldLeft(original) {
      case (json, path) => path.removeFrom(json).getOrElse(json)
    }

    val updated = append match {
      case Json.Null => deletes
      case data      => agora.api.json.deepMergeWithArrayConcat(deletes, data)
    }
    if (updated == original) {
      None
    } else {
      Option(updated)
    }
  }
}

object JsonDelta {
  def remove(jpath: JPath, theRest: JPath*): JsonDelta = JsonDelta(remove = jpath :: theRest.toList)
  def append[T: Encoder](data: T): JsonDelta           = JsonDelta(append = implicitly[Encoder[T]].apply(data))
}
