package lupin.mongo

import io.circe.Json
import org.bson.BsonValue
import org.mongodb.scala.bson._

object CirceToBson {
  def apply(json: Json): BsonDocument = asBson(json)

  def asBson(json: Json): BsonDocument = {
    if (json.isObject) {
      BsonDocument(json.noSpaces)
    } else {
      val data = Json.obj("data" -> json).noSpaces
      BsonDocument(data)
    }
  }
}

object BsonToCirce {
  def apply(bson: BsonValue): Json = asCirce(bson)

  def asCirce(bson: BsonValue): Json = {
    bson match {
      case value: BsonArray =>
        val arr: Array[BsonValue] = value.toArray(Array.ofDim[BsonValue](value.size()))
        Json.fromValues(arr.map(asCirce))
      case value: BsonBinary     => Json.fromString(value.getData.toString)
      case value: BsonBoolean    => Json.fromBoolean(value.getValue)
      case value: BsonDateTime   => Json.fromLong(value.getValue)
      case value: BsonDecimal128 => Json.fromBigDecimal(value.getValue.bigDecimalValue())
      case value: BsonDocument =>
        import scala.collection.JavaConverters._
        Json.fromFields(value.asScala.mapValues(asCirce))
      case value: BsonDouble              => Json.fromDoubleOrNull(value.doubleValue())
      case value: BsonInt32               => Json.fromInt(value.getValue)
      case value: BsonInt64               => Json.fromLong(value.getValue)
      case value: BsonJavaScript          => Json.fromString(value.getCode)
      case value: BsonJavaScriptWithScope => Json.fromString(value.getCode)
      case value: BsonMaxKey              => Json.fromString(value.toString)
      case value: BsonMinKey              => Json.fromString(value.toString)
      case value: BsonNull                => Json.Null
      case value: BsonNumber              => Json.fromLong(value.longValue())
      case value: BsonObjectId            => Json.fromString(value.getValue.toHexString)
      case value: BsonRegularExpression   => Json.fromString(value.getPattern)
      case value: BsonString              => Json.fromString(value.getValue)
      case value: BsonSymbol              => Json.fromString(value.getSymbol)
      case value: BsonTimestamp           => Json.fromLong(value.getValue)
      case value: BsonUndefined           => Json.Null
      case value: BsonElement             => Json.obj(value.getName -> asCirce(value.getValue))
      case value: BsonValue               => sys.error(s"Couldn't parse $value")
    }
  }
}
