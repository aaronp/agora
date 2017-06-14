package agora.rest.client

import java.time.LocalDateTime

object Crashes {

  case class Crash(error: Throwable, time: LocalDateTime = LocalDateTime.now)

  implicit object LocalDateTimeOrdering extends Ordering[LocalDateTime] {
    override def compare(x: LocalDateTime, y: LocalDateTime): Int = x.compareTo(y)
  }

}

case class Crashes(list: List[Crashes.Crash]) {

  import Crashes._

  override def toString = exception.toString

  def size = list.size

  def add(crash: Crash) = copy(crash :: list)

  def removeBefore(threshold: LocalDateTime = LocalDateTime.now) = {
    copy(list = list.filterNot(_.time.isBefore(threshold)))
  }

  lazy val exception = list match {
    case Nil         => new Exception("Empty Crash!")
    case head :: Nil => head.error
    case head :: tail =>
      tail.map(_.error).withFilter(_ != head.error).foreach(head.error.addSuppressed)
      head.error
  }
}
