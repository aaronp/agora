package agora.flow

import java.nio.file.{Path, StandardOpenOption}

import agora.io.LowPriorityIOImplicits
import agora.io.dao.{FromBytes, ToBytes}
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}

import scala.concurrent.{ExecutionContext, Future}

trait HistoricProcessorDao[T] {
  def executionContext: ExecutionContext

  def writeDown(index: Long, value: T): Future[Boolean]

  def at(index: Long): Future[T]

  def maxIndex: Option[Long]
}

object HistoricProcessorDao extends StrictLogging {

  class Delegate[T](underlying: HistoricProcessorDao[T]) extends HistoricProcessorDao[T] {
    override def executionContext: ExecutionContext = underlying.executionContext

    override def writeDown(index: Long, value: T) = underlying.writeDown(index, value)

    override def at(index: Long): Future[T] = underlying.at(index)

    override def maxIndex: Option[Long] = underlying.maxIndex
  }

  case class InvalidIndexException(requestedIndex: Long, msg: String) extends Exception(msg)

  def inDir[T: ToBytes : FromBytes](dir: Path, keepMost: Int = 0)(implicit ec: ExecutionContext) = {
    new FileBasedHistoricProcessorDao[T](dir, ToBytes.instance[T], FromBytes.instance[T], keepMost)
  }

  /** @param keepMost if non-zero then only 'keepMost' elements will be retained
    * @tparam T
    * @return
    */
  def apply[T](keepMost: Int = 0)(implicit ec: ExecutionContext) = new HistoricProcessorDao[T] {
    override val executionContext: ExecutionContext = ec
    private var elements = Map[Long, T]()

    private object ElementsLock

    override def maxIndex: Option[Long] = ElementsLock.synchronized {
      elements.keySet match {
        case set if set.isEmpty => None
        case set => Option(set.max)
      }
    }

    override def at(index: Long) = {

      def badIndex = {
        val msg = s"Invalid index $index. Keeping $keepMost, max index is ${elements.keySet.toList.sorted.headOption}"
        logger.error(msg)
        Future.failed[T](new InvalidIndexException(index, msg))
      }

      val opt = ElementsLock.synchronized {
        elements.get(index)
      }
      opt.fold(badIndex) { value =>
        Future.successful(value)
      }
    }

    override def writeDown(index: Long, value: T) = {
      Future.successful {
        ElementsLock.synchronized {
          elements = elements.updated(index, value)
          if (keepMost != 0) {
            val removeIndex = index - keepMost
            logger.debug(s"Removing $removeIndex")
            elements = elements - removeIndex
          }
        }
        true
      }
    }
  }

  case class FileBasedHistoricProcessorDao[T](dir: Path, toBytes: ToBytes[T], fromBytes: FromBytes[T], keepMost: Int)(
    implicit val executionContext: ExecutionContext)
    extends HistoricProcessorDao[T]
      with LazyLogging {

    import agora.io.implicits._

    private object MaxLock

    @volatile private var max = -1L

    override def maxIndex: Option[Long] = {
      Option(max).filter(_ >= 0)
    }

    override def writeDown(index: Long, value: T) = {
      Future.successful {

        MaxLock.synchronized {
          max = max.max(index)
        }
        val file = dir.resolve(index.toString).createIfNotExists()

        file.setBytes(toBytes.bytes(value), LowPriorityIOImplicits.DefaultWriteOps + StandardOpenOption.DSYNC)
        if (keepMost != 0) {
          val indexToDelete = index - keepMost
          if (dir.resolve(indexToDelete.toString).isFile) {
            dir.resolve(indexToDelete.toString).delete(false)
          }
        }
        true
      }
    }

    override def at(index: Long): Future[T] = {
      val file = dir.resolve(index.toString)
      if (file.isFile) {
        Future.fromTry(fromBytes.read(file.bytes))
      } else {
        val maxIdx = MaxLock.synchronized { max }
        val msg = s"Invalid index $index as $file doesn't exist. Max index written is $maxIdx"
        logger.error(msg)
        Future.failed[T](new InvalidIndexException(index, msg))
      }
    }
  }

}
