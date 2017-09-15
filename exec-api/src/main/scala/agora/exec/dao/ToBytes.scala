package agora.exec.dao

import java.nio.charset.Charset

import io.circe.Encoder

/**
  * Typeclass to serialize a type to bytes
  *
  * TODO - replace this with a better FP typeclass
  * @tparam T
  */
trait ToBytes[T] {

  /**
    * Converts the T to bytes
    * @param value the value to convert
    * @return the byte array representing this value
    */
  def bytes(value: T): Array[Byte]
}


object ToBytes {
  def instance[T](f: T => Array[Byte]) = new ToBytes[T] {
    override def bytes(value: T) = f(value)
  }

  implicit object identity extends ToBytes[Array[Byte]] {
    override def bytes(value: Array[Byte]): Array[Byte] = value
  }

  implicit def forJson[T: Encoder](implicit charset: Charset = Charset.defaultCharset()): ToBytes[T] = {
    instance { value =>
      implicitly[Encoder[T]].apply(value).noSpaces.getBytes(charset)
    }
  }
}