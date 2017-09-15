package agora.exec.dao

import java.nio.charset.Charset

import cats.syntax.either._
import io.circe.Decoder
import io.circe.parser._

import scala.io.Source
import scala.util.Try

/**
  * Typeclass to serialize a type from a byte array
  *
  * TODO - replace this with a better FP typeclass
  * @tparam T
  */
trait FromBytes[T] {

  /**
    * Unmarshalls the byte array into the given type
    * @param bytes the bytes to unmarshall
    * @return the T wrapped in a Try
    */
  def read(bytes: Array[Byte]): Try[T]
}

object FromBytes{

  def instance[T](f: Array[Byte] => Try[T]) = new FromBytes[T] {
    override def read(bytes: Array[Byte]) = f(bytes)
  }

  implicit def forJson[T: Decoder](implicit charset: Charset = Charset.defaultCharset()): FromBytes[T] = {
    instance { (bytes: Array[Byte]) =>
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
