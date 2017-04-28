package jabroni.api.json

import io.circe.{Encoder, Json}

trait JsonAppendable {

  protected def mergeJson[T: Encoder](json: Json, data: T, name: String = null) = {
    val namespace = Option(name).getOrElse(data.getClass.getSimpleName)
    val json: Json = implicitly[Encoder[T]].apply(data)
    val that = Json.obj(namespace -> json)
    json.deepMerge(that)
  }
}
