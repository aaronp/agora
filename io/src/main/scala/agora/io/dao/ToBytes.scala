package agora.io.dao

/**
  * Typeclass to serialize a type to bytes
  *
  * TODO - replace this with a better FP typeclass
  *
  * @tparam T
  */
trait ToBytes[T] {

  /**
    * Converts the T to bytes
    *
    * @param value the value to convert
    * @return the byte array representing this value
    */
  def bytes(value: T): Array[Byte]

  def contramap[A](f: A => T): ToBytes[A] = {
    val parent = this
    new ToBytes[A] {
      override def bytes(value: A): Array[Byte] = {
        parent.bytes(f(value))
      }
    }
  }
}

object ToBytes {
  def instance[T](f: T => Array[Byte]) = new ToBytes[T] {
    override def bytes(value: T) = f(value)
  }

  implicit object Utf8String extends ToBytes[String] {
    override def bytes(value: String): Array[Byte] = value.getBytes("UTF-8")
  }

  implicit object identity extends ToBytes[Array[Byte]] {
    override def bytes(value: Array[Byte]): Array[Byte] = value
  }

}
