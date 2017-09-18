package miniraft.state

import java.nio.file.Path

import agora.io.implicits._
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import miniraft.{AppendEntries, AppendEntriesResponse}
import miniraft.state.Log.Formatter

case class Term(t: Int) extends Ordered[Term] {
  def inc = Term(t + 1)

  override def compare(that: Term): LogIndex = t.compare(that.t)
}

object Term {
  implicit val termEncoder = Encoder.instance[Term](t => Json.fromInt(t.t))
  implicit val termDecoder = {
    Decoder.instance[Term] { h =>
      val value: Result[LogIndex] = h.as[Int]
      value.right.map(t => Term(t))
    }
  }
}

sealed trait NodeRole {
  def name: String

  def isLeader: Boolean = false

  def isFollower: Boolean = false

  def isCandidate: Boolean = false
}

case class Leader(clusterViewByNodeId: Map[NodeId, ClusterPeer]) extends NodeRole {
  override val name     = "leader"
  override val isLeader = true
}

case object Follower extends NodeRole {
  override val name       = "follower"
  override val isFollower = true
}

class Candidate private (val counter: ElectionCounter) extends NodeRole {
  override val isCandidate = true
  override val name        = "candidate"

  override def toString = s"Candidate(${counter})"

  override def equals(other: Any) = other match {
    case c: Candidate => counter == c.counter
    case _            => false
  }

  override def hashCode = 31

  def size = counter.clusterSize

  def votedFor: Set[NodeId] = counter.votedFor

  def votedAgainst: Set[NodeId] = counter.votedAgainst
}

object Candidate {

  def unapply(c: Candidate) = Option(c.counter)

  def apply(size: Int, initialFor: Set[NodeId] = Set.empty, initialAgainst: Set[NodeId] = Set.empty) = {
    require(size > 0, "can't have a totally empty cluster - surely we're a member!")
    new Candidate(new ElectionCounter(size, initialFor, initialAgainst))
  }

  def apply(cntr: ElectionCounter) = new Candidate(cntr)

  import io.circe.generic.auto._
  import io.circe.syntax._

  private case class CandidateJson(size: Int, votedFor: Set[NodeId], votedAgainst: Set[NodeId])

  implicit object CandidateEncoder extends Encoder[Candidate] {
    override def apply(a: Candidate): Json = {
      CandidateJson(a.size, a.votedFor, a.votedAgainst).asJson
    }
  }

  implicit val CandidateDecoder = implicitly[Decoder[CandidateJson]].map { cj =>
    new Candidate(new ElectionCounter(cj.size, cj.votedFor, cj.votedAgainst))
  }
}

case class LogEntry[Command](term: Term, index: LogIndex, command: Command)

object LogEntry {
  implicit def decoder[T: Decoder]: Decoder[LogEntry[T]] = {
    import io.circe.generic.auto._

    exportDecoder[LogEntry[T]].instance
  }

  implicit def encoder[T: Encoder]: Encoder[LogEntry[T]] = {
    import io.circe.generic.auto._

    val encoder = exportEncoder[LogEntry[T]].instance
    //    implicit val decoder = exportDecoder[SubmitJob].instance
    encoder
  }
}

case class ClusterPeer(matchIndex: LogIndex, nextIndex: LogIndex)

object ClusterPeer {
  def empty(nextIndex: Int) = ClusterPeer(0, nextIndex)
}

trait PersistentState[T] {
  def append(entries: List[LogEntry[T]]): PersistentState[T]

  def commit(at: LogIndex): PersistentState[T]

  def voteFor(newTerm: Term, leader: Option[NodeId]): PersistentState[T]

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

  def apply[T](workingDir: Path)(applyToStateMachine: LogEntry[T] => Unit)(implicit asBytes: Formatter[T, Array[Byte]]): PersistentState[T] = {
    val log = Log[T](workingDir.resolve("data").mkDir())(applyToStateMachine)
    apply(workingDir, log)
  }

  def apply[T](workingDir: Path, log: Log[T]) = new Dao[T](workingDir, log)

  def apply[T](initialTerm: Term = Term(1), initialVote: Option[NodeId] = None)(applyToStateMachine: LogEntry[T] => Unit) = {
    new NotReally[T](initialTerm, initialVote, applyToStateMachine)
  }

  /**
    * We're not really perisistent. Shhh ... don't tell!
    *
    * @param initialTerm
    * @param initialVote
    * @tparam T
    */
  class NotReally[T](initialTerm: Term, initialVote: Option[NodeId], applyToStateMachine: LogEntry[T] => Unit) extends PersistentState[T] {
    @volatile var vote: Option[NodeId] = initialVote
    @volatile var term: Term           = initialTerm
    private val inMemoryLog            = Log[T](applyToStateMachine)

    override def log = inMemoryLog

    override def votedFor: Option[NodeId] = vote

    override def voteFor(newTerm: Term, leader: Option[NodeId]) = {
      vote = leader
      term = newTerm
      this
    }

    override def currentTerm: Term = term

    override def commit(at: LogIndex): PersistentState[T] = {
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

    // TODO - just put both in one file
    private val votedForFile = workingDir.resolve("votedFor").createIfNotExists()
    private val currentTermFile = {
      val file = workingDir.resolve("currentTerm").createIfNotExists()
      file.text.trim match {
        case "" => file.text = "1"
        case _  => file
      }
    }

    override def currentTerm: Term = {
      val txt = currentTermFile.text
      try {
        Term(txt.toInt)
      } catch {
        case _: NumberFormatException =>
          sys.error(s"${currentTermFile} text was '${txt}'")
      }
    }

    // TODO - read caching
    override def votedFor: Option[NodeId] = Option(votedForFile.text).map(_.trim).filter(_.nonEmpty)

    override def voteFor(newTerm: Term, leader: Option[NodeId]) = {
      currentTermFile.text = newTerm.t.toString
      votedForFile.text = leader.getOrElse("")
      this
    }

    override def commit(at: LogIndex): PersistentState[T] = {
      log.commit(at)
      this
    }

    override def append(entries: List[LogEntry[T]]) = {
      entriesLog = entriesLog.append(entries)
      this
    }
  }

}

case class RaftState[T](role: NodeRole, persistentState: PersistentState[T]) {

  def becomeCandidate(clusterSize: Int, firstVote: NodeId): RaftState[T] = {
    val cntr          = new ElectionCounter(clusterSize)
    val candidateRole = Candidate(cntr)
    val newRole: NodeRole = cntr.onVote(firstVote, true) match {
      case Some(true) =>
        require(
          clusterSize == 1,
          s"We've become the leader after initially voting for ourselves and not even sending our requests, but the cluster size is $clusterSize"
        )
        candidateRole.counter.leaderRole(firstVote, lastUnappliedIndex)
      case _ => candidateRole
    }
    copy(role = newRole, persistentState = persistentState.voteFor(currentTerm.inc, Option(firstVote)))
  }

  def voteFor(term: Term, id: NodeId) = {
    require(id.nonEmpty)
    copy(role = Follower, persistentState = persistentState.voteFor(term, Option(id)))
  }
  def becomeFollower(term: Term) = {
    copy(role = Follower, persistentState = persistentState.voteFor(term, None))
  }

  def currentTerm = persistentState.currentTerm

  def log: Log[T] = persistentState.log

  def lastUnappliedIndex = log.lastUnappliedIndex

  def lastCommittedIndex = log.lastCommittedIndex

  def lastLogTerm = log.lastTerm

  def append(id: NodeId, ae: AppendEntries[T]): (RaftState[T], AppendEntriesResponse) = {
    def onMatchedTerm = {
      if (ae.prevLogIndex == lastCommittedIndex) {

        /* Append a new entry, as the leader and our log index match
         */
        val newPersistentState = ae.entry.fold(persistentState) { command =>
          val entry = LogEntry[T](currentTerm, lastUnappliedIndex + 1, command)
          persistentState.append(List(entry))
        }

        true -> copy(role = Follower, persistentState = newPersistentState)
      } else if (ae.prevLogIndex == lastUnappliedIndex && ae.entry.isEmpty) {
        /*
         * commit the previous entry
         */
        val newPS = if (ae.prevLogIndex == 0) persistentState else persistentState.commit(ae.prevLogIndex)
        true -> copy(role = Follower, persistentState = newPS)
      } else {
        false -> this
      }
    }

    val (success, appended) = if (ae.term > currentTerm) {
      // NOTE: we diverge a bit here and change our 'votedFor' to be the leader.
      // This requires some thought and may come back to bite us, as we're duplicating the use of 'votedFor' to
      // track who the leader is

      val ok = ae.entry.isEmpty
      ok -> copy(role = Follower, persistentState = persistentState.voteFor(ae.term, Option(ae.leaderId)))
    } else if (ae.term == currentTerm) {
      onMatchedTerm
    } else {
      false -> this
    }

    //    val success = (ae.term > currentTerm && ae.entry.isEmpty) || (ae.term == currentTerm && ae.prevLogIndex == lastCommittedIndex)

    appended -> AppendEntriesResponse(currentTerm, success, appended.lastUnappliedIndex)
  }
}

object RaftState {
  def apply[T](ps: PersistentState[T]): RaftState[T] = RaftState(Follower, ps)
}
