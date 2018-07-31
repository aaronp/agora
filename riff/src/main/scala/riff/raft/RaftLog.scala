package riff.raft

import java.nio.file.Path
import java.nio.file.attribute.FileAttribute

import agora.io.ToBytes
import agora.io.implicits._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable

/**
  * Represents a persistent log
  *
  * @tparam T
  */
trait RaftLog[T] {
  type Result

  /**
    * Append the given log entry w/ the given coords (term and index)
    *
    * @param coords the log term and index against which this log should be appended
    * @param data
    * @return the append result
    */
  def append(coords: LogCoords, data: T): Result

  def latestCommit(): Int

  /** @param index
    * @return the log term for the latest index
    */
  def termForIndex(index: Int): Option[Int]

  def latestAppended(): LogCoords

  def logState: LogState = {
    val LogCoords(term, index) = latestAppended()
    LogState(latestCommit(), term, index)
  }

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
//    val perm = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("wrr"))
//    Set(perm)
    Set.empty
  }

  final case class LogAppendResult(written: Path, replaced: Seq[Path] = Nil)

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
  class ForDir[T: ToBytes](val dir: Path, fileAttributes: List[FileAttribute[_]] = DefaultAttributes.toList) extends RaftLog[T] with LazyLogging {

    type Result = LogAppendResult

    import agora.io.implicits._
    private val commitFile = dir.resolve(".committed").createIfNotExists(fileAttributes: _*).ensuring(_.isFile)

    // contains the <term>:<index> of the latest entry appended
    private val latestAppendedFile = dir.resolve(".latestAppended").createIfNotExists(fileAttributes: _*).ensuring(_.isFile)
    private val LatestAppended     = """([0-9]+):([0-9]+)""".r

    override def append(coords: LogCoords, data: T): Result = {

      // if another leader was elected while we were accepting appends, then our log may be wrong
      val removedFiles = checkForOverwrite(coords)

      // update the persisted log
      writeTermEntryOnAppend(coords)

      // update our file stating what our last commit was so we don't have to search the file system
      updateLatestAppended(coords)

      // finally write our log entry
      val entryFile = entryFileForIndex(coords.index)
      entryFile.bytes = ToBytes[T].bytes(data)

      LogAppendResult(entryFile, removedFiles)
    }

    private def writeTermEntryOnAppend(coords: LogCoords) = {
      val kermit = latestCommit
      require(kermit < coords.index, s"Attempt to append $coords when the latest committed was $kermit")
      dir.resolve(s"${coords.index}.term").text = coords.term.toString
    }

    def updateLatestAppended(coords: LogCoords) = {
      // update the persisted record of the latest appended
      latestAppendedFile.text = s"${coords.term}:${coords.index}"
    }

    /**
      * we can get in this state if we've been leader and accepted some append commands from a client,
      * only to then discover there was a leader election and we were voted out, in which case we may
      * have extra, invalid uncommitted entries
      *
      * @param coords the latest append coords
      * @return the removed files
      */
    private def checkForOverwrite(coords: LogCoords): immutable.Seq[Path] = {
      val latest = latestAppended()

      if (latest.index >= coords.index) {

        require(
          coords.term > latest.term,
          s"Attempt to append $coords when our latest term is $latest. If an election took place after we were the leader, the term should've been incremented"
        )

        logger.warn(s"Received append for $coords when our last entry was $latest. Assuming we're not the leader and clobbering invalid indices")
        val deletedFiles = (coords.index to latest.index).map { index =>
          termFileForIndex(index).deleteFile()
          entryFileForIndex(index).deleteFile()
        }
        deletedFiles
      } else {
        // the coords are after our term
        require(coords.term >= latest.term, s"Attempt to append an entry w/ an earlier term that our latest entry $latest")
        require(coords.index == latest.index + 1, s"Attempt to skip a log entry by appending $coords when the latest entry was $latest")
        Nil
      }
    }

    private def entryFileForIndex(index: Int) = dir.resolve(s"${index}.entry")
    private def termFileForIndex(index: Int) = dir.resolve(s"$index.term")

    override def commit(index: Int): Seq[LogCoords] = {
      val previous = latestCommit()
      require(previous < index, s"asked to commit $index, but latest committed is $previous")

      val committed: immutable.IndexedSeq[LogCoords] = (previous to index).map { i =>
        val term = termForIndex(i).getOrElse(sys.error(s"couldn't find the term for $i"))
        LogCoords(term, i)
      }

      commitFile.text = index.toString

      committed
    }

    override def termForIndex(index: Int): Option[Int] = {
      Option(termFileForIndex(index)).filter(_.exists()).map(_.text.toInt)
    }

    override def latestCommit(): Int = {
      commitFile.text match {
        case ""    => 0
        case value => value.toInt
        case other => sys.error(s"Corrupt latest commit file ${commitFile} : >$other<")
      }
    }

    override def latestAppended(): LogCoords = {
      latestAppendedFile.text match {
        case LatestAppended(t, i) => LogCoords(term = t.toInt, index = i.toInt)
        case ""                   => LogCoords.Empty
        case other                => sys.error(s"Corrupt latest appended file ${latestAppendedFile} : >$other<")
      }
    }

    override def logState: LogState = {
      val LogCoords(term, index) = latestAppended()
      LogState(latestCommit(), term, index)
    }
  }
}
