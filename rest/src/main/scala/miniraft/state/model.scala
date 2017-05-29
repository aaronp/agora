package miniraft.state

import java.nio.file.Path

import io.circe.{Decoder, Encoder, Json}
import miniraft.state.Log.Formatter

import scala.util.Try

case class Term(t: Int) extends Ordered[Term] {
  def inc = Term(t + 1)

  override def compare(that: Term): LogIndex = t.compare(that.t)
}

sealed trait NodeRole

case class Leader(clusterViewByNodeId: Map[NodeId, ClusterPeer]) extends NodeRole

case object Follower extends NodeRole

case class Candidate private(counter: ElectionCounter) extends NodeRole

object Candidate {
  def apply(size: Int) = {
    require(size > 0, "can't have a totally empty cluster - surely we're a member!")
    new Candidate(new ElectionCounter(size))
  }
}

case class LogEntry[Command](term: Term, index: LogIndex, command: Command)

trait Log[Command] {
  def commit(index: LogIndex): Log[Command]

  def at(index: Int): Option[LogEntry[Command]]

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

  override def toString = s"Log(unapplied:${lastUnappliedIndex}, committed:${lastCommittedIndex})"

  def lastTerm = latestUnappliedEntry.map(_.term).getOrElse(Term(0))
}

object Log {

  import jabroni.domain.io.implicits._

  trait Formatter[A, B] {
    def to(a: A): B

    def from(b: B): A
  }

  object Formatter {

    implicit def fromCirce[T: Encoder : Decoder]: Formatter[T, Array[Byte]] = new Formatter[T, Array[Byte]] {
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
        case Left(err) => throw err
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

  class Dao[Command](dir: Path, asBytes: Formatter[Command, Array[Byte]]) extends Log[Command] {
    private val latestUnappliedFile = dir.resolve(".unapplied").createIfNotExists()
    private val latestCommittedFile = dir.resolve(".committed").createIfNotExists()

    override def at(index: Int): Option[LogEntry[Command]] = {
      dir.resolve(index.toString) match {
        case indexDir if indexDir.isDir =>
          val command = asBytes.from(indexDir.resolve("command").bytes)
          val term = Term(indexDir.resolve(".term").text.toInt)
          Option(LogEntry[Command](term, index, command))
        case _ => None
      }
    }

    override def latestUnappliedEntry: Option[LogEntry[Command]] = at(lastUnappliedIndex)

    override def latestCommittedEntry: Option[LogEntry[Command]] = at(lastCommittedIndex)

    override def lastUnappliedIndex = latestUnappliedFile.text match {
      case "" => 0
      case indexText => indexText.toInt
    }

    override def lastCommittedIndex = latestCommittedFile.text match {
      case "" => 0
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
        require(!indexDir.resolve(".committed").exists, s"A committed entry already exists at ${indexDir}, can't append $entry")
        indexDir.resolve("command").bytes = asBytes.to(entry.command)
        indexDir.resolve(".term").text = entry.term.t.toString
      }
      if (newEntries.nonEmpty) {
        val max = newEntries.map(_.index).max
        if (max > lastUnappliedIndex) {
          latestUnappliedFile.text = max.toString
        }
      }
      this
    }

    override def commit(index: LogIndex): Log[Command] = {
      dir.resolve(index.toString).resolve(".committed").createIfNotExists()
      if (index > lastCommittedIndex) {
        latestCommittedFile.text = index.toString
      }
      this
    }
  }

  class InMemory[Command]() extends Log[Command] {
    private var all = List[LogEntry[Command]]()
    private var committed = List[LogEntry[Command]]()

    def unapplied = all.diff(committed)

    override def append(newEntries: List[LogEntry[Command]]): Log[Command] = {
      all = newEntries ++ all
      this
    }

    override def at(index: LogIndex): Option[LogEntry[Command]] = all.find(_.index == index)

    override def commit(latestIndex: LogIndex): Log[Command] = {
      at(latestIndex).foreach { entry =>
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

  def apply[T](dir: Path)(implicit asBytes: Formatter[T, Array[Byte]]) = new Dao[T](dir, asBytes)

  def apply[T]() = new InMemory[T]()
}

case class ClusterPeer(matchIndex: LogIndex, nextIndex: LogIndex)

object ClusterPeer {
  val empty = ClusterPeer(0, 0)
}

trait PersistentState[T] {
  def append(entries: List[LogEntry[T]]): PersistentState[T]
  def commit(at :LogIndex): PersistentState[T]

  def voteFor(newTerm: Term, leader: NodeId): PersistentState[T]

  def log: Log[T]

  def currentTerm: Term

  def votedFor: Option[NodeId]

  override def toString = s"{ ${currentTerm}, votedFor ${votedFor}, $log }"

  override def hashCode = 3

  override def equals(other: Any) = {
    other match {
      case ps: PersistentState[_] =>
        currentTerm == ps.currentTerm &&
          votedFor == ps.votedFor &&
          log == ps.log
      case _ => false
    }
  }
}

object PersistentState {

  def apply[T](workingDir: Path)(implicit asBytes: Formatter[T, Array[Byte]]): PersistentState[T] = {
    import jabroni.domain.io.implicits._
    val log = Log[T](workingDir.resolve("data").mkDir())
    apply(workingDir, log)
  }

  def apply[T](workingDir: Path, log: Log[T]) = new Dao[T](workingDir, log)

  def apply[T](initialTerm: Term = Term(1), initialVote: Option[NodeId] = None) = new NotReally[T](initialTerm, initialVote)

  /**
    * We're not really perisistent. Shhh ... don't tell!
    *
    * @param initialTerm
    * @param initialVote
    * @tparam T
    */
  class NotReally[T](initialTerm: Term, initialVote: Option[NodeId]) extends PersistentState[T] {
    var vote: Option[NodeId] = initialVote
    var term: Term = initialTerm
    var commands: Log[T] = Log[T]()
    private val inMemoryLog = Log[T]()

    override def log = inMemoryLog

    override def votedFor: Option[NodeId] = vote

    override def voteFor(newTerm: Term, leader: NodeId) = {
      vote = Option(leader)
      term = newTerm
      this
    }

    override def currentTerm: Term = term

    override def commit(at :LogIndex): PersistentState[T] = {
      log.commit(at)
      this
    }
    override def append(entries: List[LogEntry[T]]) = {
      inMemoryLog.append(entries)
      this
    }
  }

  class Dao[T](workingDir: Path, initialLog: Log[T]) extends PersistentState[T] {

    private var entriesLog = initialLog

    override def log: Log[T] = entriesLog

    import jabroni.domain.io.implicits._

    // TODO - just put both in one file
    private val votedForFile = workingDir.resolve("votedFor").createIfNotExists()
    private val currentTermFile = {
      val file = workingDir.resolve("currentTerm").createIfNotExists()
      file.text.trim match {
        case "" => file.text = "1"
        case _ => file
      }
    }

    override def currentTerm: Term = Term(currentTermFile.text.toInt)

    // TODO - read caching
    override def votedFor: Option[NodeId] = Option(votedForFile.text).map(_.trim).filter(_.nonEmpty)

    override def voteFor(newTerm: Term, leader: NodeId) = {
      currentTermFile.text = newTerm.t.toString
      votedForFile.text = leader
      this
    }

    override def commit(at :LogIndex): PersistentState[T] = {
      log.commit(at)
      this
    }

    override def append(entries: List[LogEntry[T]]) = {
      entriesLog = entriesLog.append(entries)
      this
    }
  }

}

case class RaftState[T](role: NodeRole,
                        persistentState: PersistentState[T]) {

  def becomeCandidate(clusterSize: Int, firstVote: NodeId): RaftState[T] = {
    val cntr = new ElectionCounter(clusterSize)
    val candidateRole = Candidate(cntr)
    val newRole: NodeRole = cntr.onVote(firstVote, true) match {
      case Some(true) =>
        require(clusterSize == 1)
        candidateRole.counter.leaderRole(firstVote)
      case _ => candidateRole
    }
    copy(role = newRole, persistentState = persistentState.voteFor(currentTerm.inc, firstVote))
  }


  def voteFor(term: Term, id: NodeId) = {
    require(id.nonEmpty)
    copy(role = Follower, persistentState = persistentState.voteFor(term, id))
  }

  def currentTerm = persistentState.currentTerm

  def log: Log[T] = persistentState.log

  def lastUnappliedIndex = log.lastUnappliedIndex

  def lastCommittedIndex = log.lastCommittedIndex

  def lastLogTerm = log.lastTerm

  def append(ae: AppendEntries[T]) = {
    val (appended, success) = if (ae.term > currentTerm) {
      // NOTE: we diverge a bit here and change our 'votedFor' to be the leader.
      // This requires some thought and may come back to bite us, as we're duplicating the use of 'votedFor' to
      // track who the leader is
      val newState = copy(role = Follower, persistentState = persistentState.voteFor(ae.term, ae.leaderId))
      newState -> true
    } else if (ae.term == currentTerm && ae.prevLogIndex == lastCommittedIndex) {
      // the leader and our log index match
      val newState = copy(persistentState = persistentState.append(ae.entries))
      newState -> true
    } else {
      this -> false
    }

    appended -> AppendEntriesResponse(currentTerm, success, appended.lastCommittedIndex)
  }
}

object RaftState {
  def apply[T](ps: PersistentState[T]): RaftState[T] = RaftState(Follower, ps)
}