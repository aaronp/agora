package lupin.example

trait AsText[T] {
  def asText(value: T): String
}

object AsText {

  implicit object strAsTest extends AsText[String] {
    override def asText(value: String): String = value
  }

  implicit object intAsTest extends AsText[Int] {
    override def asText(value: Int): String = value.toString
  }

}
