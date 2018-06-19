package lupin.mongo.implicits

import io.circe.Json
import lupin.mongo.CirceToBson
import org.mongodb.scala.bson.BsonDocument

class CirceForMongoOps(json: Json) {
  def toBson: BsonDocument = CirceToBson(json)
}
