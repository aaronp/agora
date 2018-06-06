package lupin.mongo.implicits

import io.circe.{Encoder, Json}

trait LowPriorityMongoImplicits {

  implicit def asJsonOps(json: Json) = new CirceForMongoOps(json)

  implicit def asBsonOps[T: Encoder](value: T) = new EncoderOps(value)
}
