package agora.json

import io.circe.{Encoder, Json}

/**
  * Represents changes to some json. This is different to a [[JsonDiff]] (which represents all the diffs between
  * two documents). A delta contains instructions for what to add remove from some json
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
  def optionallyUpdate(original: Json): Option[Json] = {
    if (isEmpty) {
      None
    } else {
      val newValue = update(original)
      if (newValue == original) {
        None
      } else {
        Option(newValue)
      }
    }
  }

  def update(original: Json): Json = {
    if (isEmpty) {
      original
    } else {
      val deletes = remove.foldLeft(original) {
        case (json, path) => path.removeFrom(json).getOrElse(json)
      }

      append match {
        case Json.Null => deletes
        case data      => deepMergeWithArrayConcat(deletes, data)
      }
    }
  }
}

object JsonDelta {
  def remove(jpath: JPath, theRest: JPath*): JsonDelta = JsonDelta(remove = jpath :: theRest.toList)

  def append[T: Encoder](data: T): JsonDelta = JsonDelta(append = implicitly[Encoder[T]].apply(data))

  def diff(lhs: Json, rhs: Json) = JsonDiff(lhs, rhs).asDelta
}
