package agora.io.dao

import java.nio.file.Path
import java.time.{LocalDateTime, ZoneId, ZoneOffset}

import agora.io.dao.instances.FileTimestampDao

/**
  * Provides a means of finding values based on a time range, as well as determining the minimum/maximum time.
  *
  * @tparam T
  */
trait TimestampReader[T] {

  type Id

  def timeForId(id: Id): Option[Timestamp]

  /**
    * Reads back
    *
    * @param inRange
    * @return the entities in the given range, exclusive
    */
  def find(inRange: TimeRange): Iterator[T]

  def findIds(inRange: TimeRange): Iterator[Id]

  /** @return The first timestamp stored, if any
    */
  def first(): Option[Timestamp]

  def firstId(): Option[Id]

  /** @return The last timestamp stored, if any
    */
  def last(): Option[Timestamp]

  def lastId(): Option[Id]
}

trait TimestampWriter[T] {
  type SaveResult
  type RemoveResult

  def save(data: T, timestamp: Timestamp = TimestampDao.now()): SaveResult

  def remove(data: T): RemoveResult
}

trait TimestampDao[T] extends TimestampWriter[T] with TimestampReader[T]

object TimestampDao {

  /**
    * The file instance will store data in the following way:
    *
    * <dir>/<year>/<month><date>/<hour>/<minute>/<second_nano_id> = bytes
    *
    * and
    *
    * <dir>/<ids>/<id> = <timestamp>
    *
    * @param dir
    * @tparam T
    * @return
    */
  def apply[T: ToBytes: FromBytes: HasId](dir: Path): FileTimestampDao[T] = new FileTimestampDao(dir)

  def now(zone: ZoneId = ZoneOffset.UTC) = LocalDateTime.now(zone)

}
