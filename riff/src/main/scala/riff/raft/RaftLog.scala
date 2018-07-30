package riff.raft

import java.nio.file.Path
import java.nio.file.attribute.{FileAttribute, PosixFilePermissions}

import agora.io.ToBytes
import agora.io.implicits._

import scala.collection.immutable

/**
  * Represents a persistent log
  *
  * @tparam T
  */
trait RaftLog[T] {
  type Result

  def append(coords: LogCoords, data: T): Result

  /** @return the last committed index or zero if no entries are committed
    */
  def logState: LogState

  /**
    * Commit all the entries up to the given index.
    *
    * It is the responsibility of the node to determine whether this should be called, knowing
    * that this log is safe to commit
    *
    * @param index
    */
  def commit(index: Int): Seq[LogCoords]
}

object RaftLog {

  def apply[T: ToBytes](dir: Path, createIfNotExists: Boolean = false) = {
    require(dir.isDir || (createIfNotExists && dir.mkDirs().isDir), s"$dir is not a directory")
    new ForDir[T](dir)
  }

  val DefaultAttributes: Set[FileAttribute[_]] = {
    val perm = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("555"))
    Set(perm)
  }

  sealed trait LogAppendResult
  final case class LogReplaced(old: Path) extends LogAppendResult
  final case class LogWritten(path: Path) extends LogAppendResult

  /** This class is NOT thread safe.
    *
    * Saves entries in the form {{{
    * <dir>/<index>.entry
    * }}}
    * and
    * {{{
    *   <dir>/<index>.term
    *  }}}
    *
    * with the contents of <index>.entry being the bytes for the given value T
    *
    * When committed, the 0 byte file {{{<dir>/<index>_<term>.committed}}} will be created and the state file updated
    *
    * It also stores
    *
    * {{{<dir>/.state}}}
    *
    * @param dir
    * @param ev$1
    * @tparam T
    */
  class ForDir[T: ToBytes](val dir: Path, fileAttributes: List[FileAttribute[_]] = DefaultAttributes.toList) extends RaftLog[T] {

    type Result = LogAppendResult
    import RaftLog._
    import agora.io.implicits._
    private val stateFile: LogState.StateFile = {
      val file = dir.resolve(".state").createIfNotExists(fileAttributes: _*).ensuring(_.isFile)
      LogState.StateFile(file)
    }

    override def append(coords: LogCoords, data: T): LogAppendResult = {
      val entryFile = dir.resolve(s"${coords.index}.entry")

      require(!commitFileForIndex(coords.index).exists(), s"Attempt to append over a committed file ${commitFileForIndex(coords.index)}")
      dir.resolve(s"${coords.index}.term").text = coords.term.toString
      val result = if (entryFile.exists()) {
        entryFile.delete(false)
        LogReplaced(entryFile)
      } else {
        LogWritten(entryFile)
      }
      entryFile.bytes = ToBytes[T].bytes(data)
      result
    }

    private def commitFileForIndex(i: Int): Path = dir.resolve(s"$i.committed")

    override def commit(index: Int): Seq[LogCoords] = {
      val currentState: LogState = logState

      val range = currentState.commitIndex to index
      val committed: immutable.IndexedSeq[LogCoords] = range.map { i =>
        val term = dir.resolve(s"$i.term").text.toInt
        commitFileForIndex(i).touch(fileAttributes: _*)
        LogCoords(term, i)
      }
      stateFile.update(currentState.copy(commitIndex = index))

      committed
    }

    override def logState: LogState = stateFile.currentState()
  }
}
