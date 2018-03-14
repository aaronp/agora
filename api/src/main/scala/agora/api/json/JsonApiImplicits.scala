package agora.api.json

import java.nio.charset.Charset

import agora.io.{FromBytes, ToBytes}
import io.circe.parser.decode
import io.circe.{Decoder, Encoder}

import scala.io.Source

trait JsonApiImplicits {

  implicit def toBytesForJson[T: Encoder](implicit charset: Charset = Charset.defaultCharset()): ToBytes[T] = {
    ToBytes.lift { value =>
      implicitly[Encoder[T]].apply(value).noSpaces.getBytes(charset)
    }
  }

  implicit def fromBytesForJson[T: Decoder](implicit charset: Charset = Charset.defaultCharset()): FromBytes[T] = {
    FromBytes.lift { (bytes: Array[Byte]) =>
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
