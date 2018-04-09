package agora.flow

import java.nio.file.Path

import agora.io.{FromBytes, LowPriorityIOImplicits, ToBytes}
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * Represents the means to write down them messages (of type T) which are flowing through a [[DurableProcessor]]
  *
  * @tparam T
  */
trait DurableProcessorDao[T] {

  /** Marks the given index as the last index
    */
  def markComplete(lastIndex: Long): Unit

  /**
    * @return the last (final) index, if known
    */
  def lastIndex(): Option[Long]

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
    * @return a success flag for some reason
    */
  def writeDown(index: Long, value: T): Boolean

  /** @param index the index from which to read
    * @return the value at a particular index (which may not exist or suffer some IO error)
    */
  def at(index: Long): Try[T]

  /** @return the maximum written index (or none if there are none)
    */
  def maxIndex: Option[Long]
}

object DurableProcessorDao extends StrictLogging {

  class Delegate[T](underlying: DurableProcessorDao[T]) extends DurableProcessorDao[T] {
    override def markComplete(lastIndex: Long) = underlying.markComplete(lastIndex)

    override def lastIndex() = underlying.lastIndex()

    override def writeDown(index: Long, value: T) = underlying.writeDown(index, value)

    override def at(index: Long) = underlying.at(index)

    override def maxIndex: Option[Long] = underlying.maxIndex
  }

  case class InvalidIndexException(requestedIndex: Long, msg: String) extends Exception(msg)

  def inDir[T: ToBytes : FromBytes](dir: Path, keepMost: Int = 0)(implicit ec: ExecutionContext) = {
    new FileBasedDurableProcessorDao[T](dir, ToBytes.instance[T], FromBytes.instance[T], keepMost)
  }

  /** @param keepMost if non-zero then only 'keepMost' elements will be retained
    * @tparam T
    * @return
    */
  def apply[T](keepMost: Int = 0) = new DurableProcessorDao[T] {
    private var lastIndexOpt = Option.empty[Long]
    private var elements = Map[Long, T]()

    private object ElementsLock

    override def maxIndex: Option[Long] = ElementsLock.synchronized {
      elements.keySet match {
        case set if set.isEmpty => None
        case set => Option(set.max)
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

    /** Marks the given index as the last index
      */
    override def markComplete(lastIndex: Long): Unit = {
      lastIndexOpt = lastIndexOpt.orElse(Option(lastIndex))
    }

    override def lastIndex(): Option[Long] = lastIndexOpt
  }

  case class FileBasedDurableProcessorDao[T](dir: Path, toBytes: ToBytes[T], fromBytes: FromBytes[T], keepMost: Int)(
    implicit val executionContext: ExecutionContext)
    extends DurableProcessorDao[T]
      with LazyLogging {

    import agora.io.implicits._

    private object MaxLock

    @volatile private var max = -1L

    private lazy val lastIndexFile = dir.resolve(".lastIndex")

    override def markComplete(lastIndex: Long): Unit = {
      MaxLock.synchronized {
        max = max.max(lastIndex)
      }
      lastIndexFile.text = lastIndex.toString
    }

    override def lastIndex(): Option[Long] = {
      if (lastIndexFile.exists()) {
        Option(lastIndexFile.text.toLong)
      } else {
        None
      }
    }

    private object AsInt {
      def unapply(child : Path) : Option[Long] = {
        Try(child.fileName.toLong).toOption
      }
    }

    override def maxIndex: Option[Long] = {
      if (max == -1L) {
        val indices = dir.childrenIter.collect {
          case AsInt(idx) => idx
        }
        if (indices.isEmpty) {
          None
        } else {
          max = indices.max
          Option(max)
        }
      } else {
        Option(max)
      }
    }

    override def writeDown(index: Long, value: T) = {
      MaxLock.synchronized {
        max = max.max(index)
      }
      val file = dir.resolve(index.toString).createIfNotExists()

      file.setBytes(toBytes.bytes(value), LowPriorityIOImplicits.DefaultWriteOps)
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
        val maxIdx = MaxLock.synchronized {
          max
        }
        val msg = s"Invalid index $index as $file doesn't exist. Max index written is $maxIdx"
        logger.error(msg)
        Failure[T](new InvalidIndexException(index, msg))
      }
    }
  }

}
