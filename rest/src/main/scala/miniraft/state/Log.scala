package miniraft.state

import java.nio.file.Path

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json}

import scala.util.control.NonFatal

import agora.io.implicits._

/**
  * Represents the commit log
  *
  * @tparam Command the messages we're saving to the log
  *
  */
trait Log[Command] {
  def commit(index: LogIndex): Log[Command]

  def at(index: Int): Option[LogEntry[Command]]

  def isCommitted(index: Int): Boolean

  def latestUnappliedEntry: Option[LogEntry[Command]]

  def latestCommittedEntry: Option[LogEntry[Command]]

  /**
    * Note - implementations need to override one of the two appends
    */
  def append(newEntry: LogEntry[Command]): Log[Command] = append(newEntry :: Nil)

  def append(newEntries: List[LogEntry[Command]]): Log[Command] = newEntries.foldLeft(this)(_ append _)

  def lastUnappliedIndex: LogIndex = latestUnappliedEntry.map(_.index).getOrElse(0)

  def lastCommittedIndex: LogIndex = latestCommittedEntry.map(_.index).getOrElse(0)

  // we have no business computing a consistent hashcode for something like this which is by its nature ephemeral
  override def hashCode = 19

  override def equals(other: Any) = {
    other match {
      case ps: Log[_] =>
        lastTerm == ps.lastTerm &&
          lastCommittedIndex == ps.lastCommittedIndex
      case _ => false
    }
  }

  override def toString = s"Log(lastTerm: ${lastTerm}, unapplied:${lastUnappliedIndex}, committed:${lastCommittedIndex})"

  def lastTerm = latestUnappliedEntry.map(_.term).getOrElse(Term(0))
}

object Log {

  def apply[T](dir: Path)(applyToStateMachine: LogEntry[T] => Unit)(implicit asBytes: Formatter[T, Array[Byte]]) = new FileBasedLog[T](dir, asBytes, applyToStateMachine)

  def apply[T](applyToStateMachine: LogEntry[T] => Unit) = new InMemoryLog[T](applyToStateMachine)

  trait Formatter[A, B] {
    def to(a: A): B

    def from(b: B): A
  }

  object Formatter {

    implicit def fromCirce[T: Encoder: Decoder]: Formatter[T, Array[Byte]] = new Formatter[T, Array[Byte]] {
      val enc = implicitly[Encoder[T]]

      override def to(a: T): Array[Byte] = JsonFormatter.to(enc(a))

      override def from(b: Array[Byte]): T = {
        val json = JsonFormatter.from(b)
        json.as[T].right.get
      }
    }

    implicit object JsonFormatter extends Formatter[Json, Array[Byte]] {
      override def to(a: Json): Array[Byte] = StringFormatter.to(a.noSpaces)

      override def from(b: Array[Byte]) = io.circe.parser.parse(StringFormatter.from(b)) match {
        case Left(err)   => throw err
        case Right(json) => json
      }
    }

    implicit object StringFormatter extends Formatter[String, Array[Byte]] {
      override def to(a: String): Array[Byte] = a.getBytes

      override def from(b: Array[Byte]): String = new String(b)
    }

    // TODO - don't be stupid
    implicit object IntFormatter extends Formatter[Int, Array[Byte]] {
      override def to(a: Int): Array[Byte] = a.toString.getBytes

      override def from(b: Array[Byte]) = new String(b).toInt
    }

  }

  class FileBasedLog[Command](dir: Path, asBytes: Formatter[Command, Array[Byte]], applyToStateMachine: LogEntry[Command] => Unit) extends Log[Command] with StrictLogging {
    // both servers as a file containing the most recent committed index, a well as a marker
    // file in a log entry directory to indicate the entry is committed (e.g. applied to the state machine)
    private val CommittedFlagFileName = ".committed"

    // these files keep track of the most recent committed and unapplied indices
    // so we don't need to traverse all the files.
    private val latestUnappliedFile = dir.resolve(".unapplied").createIfNotExists()
    private val latestCommittedFile = dir.resolve(CommittedFlagFileName).createIfNotExists()

    override def at(index: Int): Option[LogEntry[Command]] = {
      dir.resolve(index.toString) match {
        case indexDir if indexDir.isDir =>
          val command = asBytes.from(indexDir.resolve("command").bytes)
          val term    = Term(indexDir.resolve(".term").text.toInt)
          Option(LogEntry[Command](term, index, command))
        case _ => None
      }
    }

    override def isCommitted(index: Int) = {
      dir.resolve(index.toString).resolve(CommittedFlagFileName).exists
    }

    override def latestUnappliedEntry: Option[LogEntry[Command]] = at(lastUnappliedIndex)

    override def latestCommittedEntry: Option[LogEntry[Command]] = at(lastCommittedIndex)

    override def lastUnappliedIndex = latestUnappliedFile.text match {
      case ""        => 0
      case indexText => indexText.toInt
    }

    override def lastCommittedIndex = latestCommittedFile.text match {
      case ""        => 0
      case indexText => indexText.toInt
    }

    /**
      * Writes the entry as:
      * {{{
      * <dir> / <index> / command    # contains the bytes of the command
      * <dir> / <index> / .term      # contains the term
      * }}}
      *
      * once an entry is committed, the empty file
      * {{{
      * <dir> / <index> / .committed
      * }}}
      *
      * will be created. If a .committed file exists in this append then an exception is thrown
      *
      * @param newEntries
      * @return
      */
    override def append(newEntries: List[LogEntry[Command]]): Log[Command] = {

      newEntries.foreach { entry =>
        val indexDir = dir.resolve(entry.index.toString)
        logger.debug(s"Appending $entry under $indexDir")
        require(!isCommitted(entry.index), s"A committed entry already exists at ${indexDir}, can't append $entry")
        indexDir.resolve("command").bytes = asBytes.to(entry.command)
        indexDir.resolve(".term").text = entry.term.t.toString
      }
      if (newEntries.nonEmpty) {
        val max = newEntries.map(_.index).max
        if (max > lastUnappliedIndex) {
          logger.debug(s"latestUnappliedFile at '$latestUnappliedFile' is now $max")
          latestUnappliedFile.text = max.toString
        }
      }
      this
    }

    override def commit(index: LogIndex): Log[Command] = {
      val recordDir = dir.resolve(index.toString)
      at(index) match {
        case None =>
          logger.error(s"Told to commit $index, but no log index exists at $recordDir")
        case Some(entry) =>
          logger.debug(s"Committing $index ")
          recordDir.resolve(CommittedFlagFileName).createIfNotExists()
          if (index > lastCommittedIndex) {
            logger.debug(s"$index committed, applying to state machine...")
            try {
              applyToStateMachine(entry)
            } catch {
              case NonFatal(e) =>
                logger.debug(s"Applying committed entry $entry threw $e", e)
            }
            latestCommittedFile.text = index.toString
          } else if (index == lastCommittedIndex) {
            logger.debug(s"Ignoring duplicate commit for $index")
          } else {
            sys.error(s"Can't commit entry at index $index as last committed index is $lastCommittedIndex")
          }
      }
      this
    }
  }

  class InMemoryLog[Command](applyToStateMachine: LogEntry[Command] => Unit) extends Log[Command] {
    private var unapplied = List[LogEntry[Command]]()
    private var committed = List[LogEntry[Command]]()

    override def append(newEntries: List[LogEntry[Command]]): Log[Command] = {
      unapplied = newEntries ++ unapplied
      this
    }

    override def at(index: LogIndex): Option[LogEntry[Command]] = unapplied.find(_.index == index)

    override def isCommitted(index: Int): Boolean = committed.exists(_.index == index)

    override def commit(latestIndex: LogIndex): Log[Command] = {
      at(latestIndex).foreach { entry =>
        applyToStateMachine(entry)
        committed = entry :: committed
      }
      this
    }

    override def latestUnappliedEntry: Option[LogEntry[Command]] = unapplied.headOption.map { _ =>
      unapplied.maxBy(_.index)
    }

    override def latestCommittedEntry: Option[LogEntry[Command]] = committed.headOption.map { _ =>
      committed.maxBy(_.index)
    }
  }

}
