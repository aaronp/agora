package agora.exec.dao

import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import java.time.Instant

import agora.exec.dao.TimestampDao.Timestamp
import cats.{Functor, Monad}
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, Future}

case class StampedInstance[T](id: String, thing: T, time: Timestamp)

trait TimestampReader[T] {
  def find(from: TimestampDao.Timestamp, to: TimestampDao.Timestamp): Future[List[StampedInstance[T]]]
  def first(): Future[Option[Timestamp]]
  def last(): Future[Option[Timestamp]]
}

trait TimestampWriter[T] {
  def save(data: T, timestamp: TimestampDao.Timestamp = TimestampDao.now): Unit
  def remove(data: T): Unit
}

trait TimestampDao[T] extends TimestampWriter[T] with TimestampReader[T]

object TimestampDao {

  def apply[T: ToBytes: HasId](dir: Path) = new FileInstance(dir)

  trait HasId[T] {
    def id(value: T): String
  }
  object HasId {
    def instance[T](f: T => String) = new HasId[T] {
      override def id(value: T) = f(value)
    }
    case class identity(value: String) extends HasId[String] {
      override def id(value: String) = value
    }
  }

  trait ToBytes[T] {
    def bytes(value: T): Array[Byte]
  }

  object ToBytes {
    def instance[T](f: T => Array[Byte]) = new ToBytes[T] {
      override def bytes(value: T) = f(value)
    }
    implicit def forJson[T: Encoder](charset: Charset = Charset.defaultCharset()): ToBytes[T] = {
      instance { value =>
        implicitly[Encoder[T]].apply(value).noSpaces.getBytes(charset)
      }
    }
  }

  type Timestamp = Instant

  def now() = Instant.now()

  def epoch(ts: Timestamp) = ts.toEpochMilli

  /**
    * Writes stuff down in the directory structures:
    *
    * <date>/<hour>/<id>
    *
    * and
    *
    */
  class FileInstance[T: ToBytes: HasId](dir: Path) extends TimestampDao[T] {
    override def save(data: T, timestamp: Timestamp): Unit = {}

    override def remove(data: T): Unit = ???

    def get(id: String): Future[Option[T]] = {
      ???
    }

    override def find(from: Timestamp, to: Timestamp) = {
      ???
    }

    override def first() = ???

    override def last() = ???
  }

}
