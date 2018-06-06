package lupin.mongo.implicits

import io.circe.Encoder
import lupin.mongo.CirceToBson
import org.mongodb.scala.bson.BsonDocument

class EncoderOps[T](value: T)(implicit enc: Encoder[T]) {
  def asBson: BsonDocument = {
    import io.circe.syntax._
    CirceToBson(value.asJson)
  }
}
