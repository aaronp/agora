package agora.api.config

import com.typesafe.config.{Config, ConfigException}
import io.circe.{Decoder, Json}
import io.circe.parser._

import scala.collection.JavaConverters._

/**
  * We could also consider libraries such as pure config
  */
case class JsonConfig(config: Config) {

  def configAsJson: Json = {
    import agora.config.implicits._
    parse(config.json).right.get
  }

  // TODO - use pure config
  def as[T: Decoder](key: String): T = {
    val json = try {
      JsonConfig.asJson(config.getConfig(key))
    } catch {
      case _: ConfigException.WrongType =>
        try {
          Json.fromString(config.getString(key))
        } catch {
          case _: ConfigException.WrongType =>
            try {
              val confs = config.getConfigList(key).asScala
              Json.fromValues(confs.map(JsonConfig.asJson))
            } catch {
              case _: ConfigException.WrongType =>
                val confs = config.getStringList(key).asScala
                Json.fromValues(confs.map(Json.fromString))
            }
        }
    }

    JsonConfig.cast(json, key)
  }
}

object JsonConfig {

  trait LowPriorityConfigImplicits {
    implicit def asRichJsonConfig(conf: Config): JsonConfig = JsonConfig(conf)
  }

  object implicits extends LowPriorityConfigImplicits

  def cast[T: Decoder](json: Json, key: String): T = {
    json.as[T] match {
      case Right(m) => m
      case Left(err) => {
        sys.error(s"Couldn't parse the client subscription '$key' $json : $err")
      }
    }
  }

  def asJson(c: Config): Json = {
    import agora.config.implicits._
    _root_.io.circe.parser.parse(c.json).right.get
  }
}
