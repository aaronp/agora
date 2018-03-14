package agora.json

import agora.json.JExpression.LowPriorityJExpressionImplicits
import agora.json.JPredicate.LowPriorityPredicateImplicits
import agora.json.RichJsonOps.LowPriorityJsonOpsImplicits
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe.java8.time.TimeInstances
import io.circe.{Decoder, Encoder, Json}

/**
  *
  */
trait AgoraJsonImplicits extends TimeInstances with LowPriorityPredicateImplicits with LowPriorityJExpressionImplicits with LowPriorityJsonOpsImplicits {

  implicit def asRichJsonOps(json: Json) = RichJsonOps(json)

  implicit val ThrowableEncoder: Encoder[Throwable] = {
    Encoder.encodeString.contramap((e: Throwable) => e.getMessage)
  }
  implicit val ThrowableDecoder: Decoder[Throwable] = {
    Decoder.decodeString.map(err => new Exception(err))
  }

  implicit val ConfigEncoder: Encoder[Config] = {
    Encoder.encodeString.contramap((conf: Config) => conf.root().render(ConfigRenderOptions.concise()))
  }
  implicit val ConfigDecoder: Decoder[Config] = {
    Decoder.decodeString.map(str => ConfigFactory.parseString(str))
  }

}

object AgoraJsonImplicits extends AgoraJsonImplicits
