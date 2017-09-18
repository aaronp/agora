package agora.api.json

import agora.io.dao.{FromBytes, ToBytes}
import io.circe.Encoder

import java.nio.charset.Charset

import scala.io.Source
import cats.syntax.either._
import io.circe.Decoder
import io.circe.parser._

/**
  * Implicit conversions for combining [[ToBytes]] and [[FromBytes]] w/ circe [[Encoder]]s and [[Decoder]]s
  */
trait JsonByteImplicits {

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
}

object JsonByteImplicits extends JsonByteImplicits
