package agora.exec.dao

import java.nio.file.Path
import java.time.{LocalDateTime, ZoneOffset}

import agora.exec.dao.instances.FileInstance

/**
  * Provides a means of finding values based on a time range, as well as determining the minimum/maximum time.
  *
  * @tparam T
  */
trait TimestampReader[T] {

  /**
    * Reads back
    * @param inRange
    * @return the entities in the given range, exclusive
    */
  def find(inRange: TimeRange): Iterator[T]

  /** @return The first timestamp stored, if any
    */
  def first(): Option[Timestamp]

  /** @return The last timestamp stored, if any
    */
  def last(): Option[Timestamp]
}

trait TimestampWriter[T] {
  def save(data: T, timestamp: Timestamp = TimestampDao.now): Unit

  def remove(data: T): Unit
}

trait TimestampDao[T] extends TimestampWriter[T] with TimestampReader[T]

object TimestampDao {

  def apply[T: ToBytes : FromBytes : HasId](dir: Path): FileInstance[T] = new FileInstance(dir)

  def now() = LocalDateTime.now(ZoneOffset.UTC)


}
