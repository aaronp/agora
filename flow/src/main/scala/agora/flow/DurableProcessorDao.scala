package agora.flow

import java.nio.file.{Path, StandardOpenOption}

import agora.io.LowPriorityIOImplicits
import agora.io.dao.{FromBytes, ToBytes}
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait DurableProcessorDao[T] {
//  def executionContext: ExecutionContext

  /**
    * Note - 'writeDown' will be called from the same thread w/ sequential indices.
    * the futures returned should also complete in order.
    *
    * e.g. calling
    * val fut1 = writeDown(1, value1)
    * val fut2 = writeDown(2, value2)
    *
    * should never have fut2 complete before fut1
    *
    * @param index
    * @param value
    * @return
    */
  def writeDown(index: Long, value: T): Boolean

//  def at(index: Long): Future[T]
  def at(index: Long): Try[T]

  def maxIndex: Option[Long]
}

object DurableProcessorDao extends StrictLogging {

  class Delegate[T](underlying: DurableProcessorDao[T]) extends DurableProcessorDao[T] {
//    override def executionContext: ExecutionContext = underlying.executionContext

    override def writeDown(index: Long, value: T) = underlying.writeDown(index, value)

    override def at(index: Long) = underlying.at(index)

    override def maxIndex: Option[Long] = underlying.maxIndex
  }

  case class InvalidIndexException(requestedIndex: Long, msg: String) extends Exception(msg)

  def inDir[T: ToBytes: FromBytes](dir: Path, keepMost: Int = 0)(implicit ec: ExecutionContext) = {
    new FileBasedDurableProcessorDao[T](dir, ToBytes.instance[T], FromBytes.instance[T], keepMost)
  }

  /** @param keepMost if non-zero then only 'keepMost' elements will be retained
    * @tparam T
    * @return
    */
  def apply[T](keepMost: Int = 0) = new DurableProcessorDao[T] {
//    override val executionContext: ExecutionContext = ec
    private var elements = Map[Long, T]()

    private object ElementsLock

    override def maxIndex: Option[Long] = ElementsLock.synchronized {
      elements.keySet match {
        case set if set.isEmpty => None
        case set                => Option(set.max)
      }
    }

    override def at(index: Long) = {

      def badIndex: Try[T] = {
        val msg = s"Invalid index $index. Keeping $keepMost, max index is ${elements.keySet.toList.sorted.headOption}"
        logger.error(msg)
        Failure[T](new InvalidIndexException(index, msg))
      }

      val opt = ElementsLock.synchronized {
        elements.get(index)
      }
      opt.fold(badIndex) { value =>
        Success(value)
      }
    }

    override def writeDown(index: Long, value: T) = {
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

  case class FileBasedDurableProcessorDao[T](dir: Path, toBytes: ToBytes[T], fromBytes: FromBytes[T], keepMost: Int)(
      implicit val executionContext: ExecutionContext)
      extends DurableProcessorDao[T]
      with LazyLogging {

    import agora.io.implicits._

    private object MaxLock

    @volatile private var max = -1L

    override def maxIndex: Option[Long] = {
      Option(max).filter(_ >= 0)
    }

    override def writeDown(index: Long, value: T) = {
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

    override def at(index: Long) = {
      val file = dir.resolve(index.toString)
      if (file.isFile) {
        fromBytes.read(file.bytes)
      } else {
        val maxIdx = MaxLock.synchronized { max }
        val msg    = s"Invalid index $index as $file doesn't exist. Max index written is $maxIdx"
        logger.error(msg)
        Failure[T](new InvalidIndexException(index, msg))
      }
    }
  }

}
