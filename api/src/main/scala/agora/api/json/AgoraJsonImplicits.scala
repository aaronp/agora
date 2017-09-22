package agora.api.json

import agora.io.dao.{FromBytes, ToBytes}
import io.circe.Encoder
import java.nio.charset.Charset

import scala.io.Source
import cats.syntax.either._
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe.Decoder
import io.circe.java8.time.TimeInstances
import io.circe.parser._

/**
  * Implicit conversions for combining [[ToBytes]] and [[FromBytes]] w/ circe [[Encoder]]s and [[Decoder]]s
  */
trait AgoraJsonImplicits extends TimeInstances {

  implicit def toBytesForJson[T: Encoder](implicit charset: Charset = Charset.defaultCharset()): ToBytes[T] = {
    ToBytes.instance { value =>
      implicitly[Encoder[T]].apply(value).noSpaces.getBytes(charset)
    }
  }

  implicit def fromBytesForJson[T: Decoder](implicit charset: Charset = Charset.defaultCharset()): FromBytes[T] = {
    FromBytes.instance { (bytes: Array[Byte]) =>
      val src: Source = scala.io.Source.fromBytes(bytes, charset.name())
      val jsonString = try {
        src.getLines.mkString("")
      } finally {
        src.close
      }

      decode[T](jsonString).toTry
    }
  }

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
