package riff.impl

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import agora.io.ToBytes
import agora.io.implicits._
import riff.airc.LogState

trait RaftLog[T] {
  def append(term: Int, data: T): LogState
  def commit(index: Int): Unit
}

object RaftLog {

  def apply[T: ToBytes](dir: Path, createIfNotExists: Boolean = false) = {
    require(dir.isDir || (createIfNotExists && dir.mkDirs().isDir), s"$dir is not a directory")
    new ForDir[T](dir)
  }

  class ForDir[T: ToBytes](dir: Path) extends RaftLog[T] {

    import RaftLog._

    private val lastLogIndex = new AtomicInteger(RaftLog.maxIndex(dir))
    private val commitFile = dir.resolve(".lastCommitted")
    private var lastCommitted: Int = if (commitFile.exists()) {
      commitFile.text.toInt
    } else 0


    override def append(term: Int, data: T): LogState = {
      val index = lastLogIndex.incrementAndGet()
      dir.resolve(index.toString).bytes = ToBytes[T].bytes(data)
      LogState(term, index, lastCommitted)
    }

    override def commit(index: Int): Unit = {
      commitFileForEntry(dir.resolve(index.toString)).touch()
      lastCommitted = index
      commitFile.text = index.toString
    }
  }

  object RaftLog {
    // the naming convention is <index>_<term>
    val FileP = "([0-9]+)".r

    def maxIndex(dir: Path) = {
      dir.children.flatMap(indexForFile) match {
        case Seq() => 0
        case found => found.max
      }
    }

    def commitFileForEntry(file: Path) = file.resolveSibling(file.getFileName + ".committed")

    def termFileForEntry(file: Path) = file.resolveSibling(file.getFileName + ".committed")

    def isCommitted(file: Path): Boolean = commitFileForEntry(file).exists()

    def indexForFile(file: Path): Option[Int] = file.getFileName.toString match {
      case FileP(index) =>
        Option(index.toInt)
      case _ => None
    }

  }

}