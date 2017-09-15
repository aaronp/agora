package agora.exec.dao
package instances


import java.nio.file.Path
import java.time.LocalDate

import agora.api.io.implicits._
import agora.exec.dao.{FromBytes, HasId, TimeRange, Timestamp, TimestampDao, ToBytes}

import scala.util.Try


/**
  * Writes stuff down in the directory structures:
  *
  * <date>/<hour>/<minute>/<id>
  *
  *
  */
class FileInstance[T: ToBytes : FromBytes : HasId](dir: Path) extends TimestampDao[T] {

  import FileInstance._

  private val idFor = implicitly[HasId[T]]
  private val toBytes = implicitly[ToBytes[T]]
  private val fromBytes = implicitly[FromBytes[T]]
  lazy val idsDir = dir.resolve("ids").mkDirs()

  private def idLink(id: String) = idsDir.resolve(id)

  override def save(data: T, timestamp: Timestamp): Unit = {
    val id = idFor.id(data)
    val file = saveTimestampedFile(data, timestamp, id)
    file.linkToFrom(idLink(id))
  }

  def saveTimestampedFile(data: T, timestamp: Timestamp, id : String): Path = {
    val fileName = s"${timestamp.getSecond}_${timestamp.getNano}_$id"
    val minuteDir = resolveDir(timestamp)
    minuteDir.resolve(fileName).bytes = toBytes.bytes(data)
  }

  private def resolveDir(timestamp: Timestamp): Path = {
    dir.
      resolve(dateDir(timestamp)).
      resolve(timestamp.getHour.toString).
      resolve(timestamp.getMinute.toString)
  }

  private def dateDir(timestamp: Timestamp): String = {
    s"${timestamp.getYear}-${timestamp.getMonthValue}-${timestamp.getDayOfMonth}"
  }

  override def remove(data: T): Unit = {
    val id = idFor.id(data)
    get(id)
    idLink(id).delete(false)
  }

  def get(id: String): Option[T] = {
    read(idLink(id))
  }

  private def read(file: Path): Option[T] = {
    if (file.exists) {
      fromBytes.read(file.bytes).toOption
    } else {
      None
    }
  }

  private def firstEntry = dateDirs.sorted.flatMap(_.first).headOption

  override def first = firstEntry.map(_.timestamp)

  private def lastEntry = dateDirs.sorted.reverse.flatMap(_.last).headOption

  override def last = lastEntry.map(_.timestamp)

  /**
    * With the directory structure:
    *
    * <date>/<hr>/<min>/<second>_<nanos>_<id>
    *
    * we can know to include *all* the subdirectories or if we have to filter them
    *
    * @param range
    * @return
    */
  override def find(range: TimeRange): Iterator[T] = {
    findEntries(range).map(_.file).flatMap(read)
  }

  final def find(from: Timestamp, to: Timestamp): Iterator[T] = find(TimeRange(from, to))

  def findIds(range: TimeRange): Iterator[String] = findEntries(range).map(_.id)

  private def findEntries(range: TimeRange) = dateDirs.iterator.flatMap(_.inRange(range))

  private def dateDirs = {
    dir.children.collect {
      case DateDir(dateDir) => dateDir
    }
  }
}


object FileInstance {

  // second_nanosecond_id
  private val TimeFileNameR =
    """(\d+)_(\d+)_(.*)""".r

  /**
    * A saved file is in the form <seconds>_<nanos>_<id>
    */
  object SavedFile {
    def unapply(file: Path): Option[(Int, Int, String, Path)] = {
      file.fileName match {
        case TimeFileNameR(second, nanos, id) => Option((second.toInt, nanos.toInt, id, file))
        case _ => None
      }
    }
  }

  private object IntDir {
    def unapply(dir: Path): Option[Int] = {
      Try(dir.fileName.toInt).toOption
    }
  }


  private object DateDir {
    private val YearMonthDayR = """(\d\d\d\d)-(\d{1,2})-(\d{1,2})""".r

    def unapply(dir: Path): Option[DateDir] = {
      dir.fileName match {
        case YearMonthDayR(y, m, d) => Option(DateDir(LocalDate.of(y.toInt, m.toInt, d.toInt), dir))
        case _ => None
      }
    }
  }

  /**
    * Represents a 'date' (year-month-date) directory
    */
  private case class DateDir(date: LocalDate, dateDir: Path) extends Ordered[DateDir] {
    def first: Option[StampedFile] = hours.sorted.flatMap(_.first).headOption

    def last: Option[StampedFile] = hours.sorted.reverse.flatMap(_.last).headOption

    def inRange(range: TimeRange): Iterator[StampedFile] = {
      hours.iterator.flatMap {
        case hourDir if range.completelyContainsDateAndHour(date, hourDir.hour) =>
          hourDir.hourDir.nestedFiles.collect {
            case StampedFile(sf) => sf
          }
        case hourDir => hourDir.inRange(range)
      }
    }

    def hours = dateDir.children.collect {
      case dir@IntDir(hour) => HourDir(date, hour, dir)
    }

    override def compare(that: DateDir): Int = date.compareTo(that.date)
  }

  private case class HourDir(date: LocalDate, hour: Int, hourDir: Path) extends Ordered[HourDir] {
    def dateAndHour = date.atTime(hour, 0)

    def first: Option[StampedFile] = minutes.sorted.flatMap(_.first).headOption

    def last: Option[StampedFile] = minutes.sorted.reverse.flatMap(_.last).headOption

    def inRange(range: TimeRange): Iterator[StampedFile] = {
      minutes.iterator.flatMap {
        case minuteDir if range.completelyContainsDateHourAndMinute(date, hour, minuteDir.minute) =>
          minuteDir.minuteDir.nestedFiles.collect {
            case StampedFile(sf) => sf
          }
        case minuteDir => minuteDir.inRange(range)
      }
    }

    def minutes = hourDir.children.collect {
      case dir@IntDir(minute) => MinuteDir(date, hour, minute, dir)
    }

    override def compare(that: HourDir): Int = dateAndHour.compareTo(that.dateAndHour)
  }

  private case class MinuteDir(date: LocalDate, hour: Int, minute: Int, minuteDir: Path) extends Ordered[MinuteDir] {
    def dateAndTime = date.atTime(hour, minute)

    def first: Option[StampedFile] = files.sortBy(_.timestamp).headOption

    def last: Option[StampedFile] = files.sortBy(_.timestamp).lastOption

    def inRange(range: TimeRange) = files.filter(file => range.contains(file.timestamp)).iterator

    def files: Array[StampedFile] = {
      minuteDir.children.collect {
        case SavedFile(second, nano, id, file) =>
          val timestamp = date.atTime(hour, minute, second, nano)
          StampedFile(timestamp, id, file)
      }
    }

    override def compare(that: MinuteDir): Int = dateAndTime.compareTo(that.dateAndTime)
  }

  private case class StampedFile(timestamp: Timestamp, id: String, file: Path)

  private object StampedFile {
    def unapply(file: Path): Option[StampedFile] = {
      if (file.isFile) {
        file match {
          case SavedFile(second, nano, id, file) =>
            for {
              minuteDir@IntDir(minute) <- file.parent
              hourDir@IntDir(hour) <- minuteDir.parent
              DateDir(dateDir) <- hourDir.parent
            } yield {
              val timestamp = dateDir.date.atTime(hour, minute, second, nano)
              StampedFile(timestamp, id, file)
            }
          case _ => None
        }
      } else {
        None
      }
    }
  }


}