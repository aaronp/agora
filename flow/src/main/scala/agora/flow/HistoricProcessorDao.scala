package agora.flow

import java.nio.file.Path

import agora.io.dao.{FromBytes, ToBytes}
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}

import scala.concurrent.{ExecutionContext, Future}

trait HistoricProcessorDao[T] {
  def executionContext: ExecutionContext

  def writeDown(index: Long, value: T): Unit

  def at(index: Long): Future[T]

  def maxIndex: Option[Long]
}

object HistoricProcessorDao extends StrictLogging {

  case class InvalidIndexException(requestedIndex: Long) extends Exception(s"Invalid index $requestedIndex")

  private def invalidIndex[T](idx: Long): Future[T] = {
    logger.error(s"Invalid index $idx")
    Future.failed(new InvalidIndexException(idx))
  }

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

    override def maxIndex: Option[Long] = elements.keySet match {
      case set if set.isEmpty => None
      case set => Option(set.max)
    }

    override def at(index: Long) = {
      elements.get(index).fold(invalidIndex[T](index)) { value =>
        Future.successful(value)
      }
    }

    override def writeDown(index: Long, value: T): Unit = {
      elements = elements.updated(index, value)
      if (keepMost != 0) {
        val removeIndex = index - keepMost
        elements = elements - removeIndex
      }
    }
  }

  case class FileBasedHistoricProcessorDao[T](dir: Path, toBytes: ToBytes[T], fromBytes: FromBytes[T], keepMost: Int)(
    implicit val executionContext: ExecutionContext)
    extends HistoricProcessorDao[T]
      with LazyLogging {

    import agora.io.implicits._

    @volatile private var max = -1L

    override def maxIndex: Option[Long] = {
      Option(max).filter(_ >= 0)
    }

    override def writeDown(index: Long, value: T): Unit = {
      max = max.max(index)
      dir.resolve(index.toString).bytes = toBytes.bytes(value)
      if (keepMost != 0) {
        val indexToDelete = index - keepMost
        if (dir.resolve(indexToDelete.toString).isFile) {
          dir.resolve(indexToDelete.toString).delete(false)
        }
      }
    }

    override def at(index: Long): Future[T] = {
      val file = dir.resolve(index.toString)
      if (file.isFile) {
        Future.fromTry(fromBytes.read(file.bytes))
      } else {
        invalidIndex[T](index)
      }
    }
  }

}
