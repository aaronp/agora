package miniraft

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import akka.stream.Materializer
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import jabroni.rest.client.RestClient

import scala.collection.immutable
import scala.concurrent.{Future, Promise}
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits._

sealed trait Role {
  def isLeader: Boolean = false

  def isFollower: Boolean = false

  def isCandidate: Boolean = false
}

case object Follower extends Role {
  override val isFollower = true
}

case object Leader extends Role {
  override val isLeader = true
}

case class CandidateState(id: NodeId, nodes: Set[NodeId], responses: Map[NodeId, RequestVoteResponse] = Map.empty) extends Role {
  override val isCandidate = true

  def hasMajority = isMajority(responses.values.filter(_.granted).size, nodes.size)

  def newRequestVotes(term: Term, lastLogTerm: Term, commitIndex: CommitIndex) = {
    nodes.map { to =>
      RequestVote(id, to, term, commitIndex, lastLogTerm)
    }
  }

  def onResponse(resp: RequestVoteResponse): CandidateState = {
    require(!responses.contains(resp.from))
    require(nodes.contains(resp.from))
    copy(id, nodes, responses.updated(resp.from, resp))
  }
}

case class Term(value: Int) extends Ordered[Term] {
  def inc = copy(value = value + 1)

  override def compare(that: Term): CommitIndex = value.compareTo(that.value)
}

class Journal(initialCommitIndex: CommitIndex, initialLogTerm: Term) {

  var commitIndex: CommitIndex = initialCommitIndex
  var lastLogTerm: Term = initialLogTerm
  var saved = Vector[(Term, CommitIndex, Any)]()
  var committed = Vector[(Term, CommitIndex, Any)]()

  def append[T](term: Term, stuff: List[T]) = {
    val values = stuff.zipWithIndex.map {
      case (x, i) => (term, i + commitIndex, x)
    }
    saved = saved ++ values
  }

  def commit() = {
    committed = committed ++ saved
    saved = Vector.empty
    commitIndex = commitIndex + saved.size
  }
}

object Journal {
  def apply(commitIndex: CommitIndex = 0, lastLogTerm: Term = Term(0)) = new Journal(commitIndex, lastLogTerm)
}

class RaftNode private(val id: NodeId, journal: Journal, initialTerm: Term) {
  var currentTerm: Term = initialTerm

  def commitIndex = journal.commitIndex

  var votedFor: Option[NodeId] = None
  private var state: Role = Follower

  private def become(newState: Role) = {
    logger.info(s"$id moving from $state into $newState")
    state = newState
  }

  def isFollower = state.isFollower

  def isCandidate = state.isCandidate

  def isLeader: Boolean = state.isLeader

  private var knownStateById: Map[NodeId, PeerState] = Map.empty

  def clusterView = knownStateById

  // some mechanism to make each node aware of who's in the cluster
  def setNodes(ids: Set[NodeId]) = {
    knownStateById = ids.map { id =>
      id -> PeerState(Term(0), 1, 0)
    }.toMap
  }

  def clientAppend[T: Encoder : Decoder](entries: List[T], broadcast: Broadcast): Future[Boolean] = {
    require(isLeader)
    val promise = Promise[Boolean]()
    val receivedYes = new AtomicInteger(0)
    val receivedNo = new AtomicInteger(0)
    val total = broadcast.nodeIds.size

    journal.append(currentTerm, entries)

    newAppendEntries(entries).map { msg =>
      broadcast.onAppendEntries(msg).map { response =>
        val received = if (response.success) {
          receivedYes.incrementAndGet()
        } else {
          receivedNo.incrementAndGet()
        }
        if (isMajority(received, total)) {
          journal.commit()
          promise.complete(Try(response.success))
        }
      }
    }
    promise.future
  }

  def onRequestVoteResponse(response: RequestVoteResponse, broadcast: Broadcast) = synchronized {
    logger.info(s"$id onRequestVoteResponse $response w/ $state")
    state match {
      case c: CandidateState =>
        val newState: CandidateState = c.onResponse(response)
        if (newState.hasMajority) {
          logger.info(s"$id becoming the leader after $response")
          become(Leader)
          //          newAppendEntries(Nil).foreach(broadcast.onAppendEntries)
          ???
        } else {
          logger.info(s"$id moving to $newState after $response")
          become(newState)
        }
      case other =>
        logger.info(s"$id ignoring $response as it is in $other")
    }
  }

  def handleAppendEntriesResponse[T](resp: AppendEntriesResponse) = synchronized {
    if (isLeader) {
      knownStateById = knownStateById.updated(resp.from, PeerState(resp.term, resp.matchIndex, resp.matchIndex + 1))
    }

    if (resp.success) {
      val ackd = knownStateById.values.filter(_.matchIndex == resp.matchIndex).size
      val total = knownStateById.size
      if (isMajority(ackd, total)) {
        journal.commit
        // TODO - reply to original client request
      }
    }
  }

  def newAppendEntries[T: Encoder : Decoder](entries: List[T]): immutable.Iterable[AppendEntries[T]] = {
    val commitIndex = journal.commitIndex
    knownStateById.collect {
      case (to, state@PeerState(t, n, m)) if to != id =>
        val entriesForIndex = entries
        AppendEntries(id, to, currentTerm, state, entriesForIndex, commitIndex)
    }
  }

  def onHeartbeatTimeout(broadcast: Broadcast): Set[(RequestVote, Future[RequestVoteResponse])] = synchronized {
    val newState = CandidateState(id, broadcast.nodeIds)
    currentTerm = currentTerm.inc
    val requests = newState.newRequestVotes(currentTerm, journal.lastLogTerm, journal.commitIndex)
    become(newState)
    requests.map(r => r -> broadcast.onRequestVote(r))
  }

  def handleAppendEntries[T](req: AppendEntries[T]): AppendEntriesResponse = {
    require(req.to == id)
    if (currentTerm < req.term) {
      require(!isLeader)
      become(Follower)
    }
    val ok: Boolean = req.term == currentTerm && req.prevIndex == journal.commitIndex
    if (ok) {
      journal.append(currentTerm, req.entries)

      //journal.commit // ??? when?
    }
    AppendEntriesResponse(id, req.from, currentTerm, ok, journal.commitIndex + 1)
  }

  def handleRequestVote(req: RequestVote): RequestVoteResponse = synchronized {
    def grantVote = {
      currentTerm = req.term
      votedFor = Option(req.from)
      RequestVoteResponse(id, req.from, req.term, true)
    }

    def rejectVote = {
      RequestVoteResponse(id, req.from, currentTerm, false)
    }

    if (req.term > currentTerm) {
      grantVote
    } else {
      votedFor match {
        case None if (req.term == currentTerm) => grantVote
        case _ => rejectVote
      }
    }
  }
}


object RaftNode {
  def apply(id: NodeId = UUID.randomUUID().toString) = new RaftNode(id, Journal(0, Term(0)), Term(1))
}

trait ClusterClient {
  def append[T: Encoder : Decoder](value: List[T]): Future[Boolean]
}

case class PeerState(term: Term, nextIndex: CommitIndex, matchIndex: CommitIndex)

object PeerState {
  val initial = PeerState(Term(0), 1, 0)
}

sealed trait Message

sealed trait RaftRequest {
  def from: NodeId

  def to: NodeId
}

sealed trait RaftResponse

trait Transport {

  def onAppendEntries[T: Encoder : Decoder](req: AppendEntries[T]): Future[AppendEntriesResponse]

  def onRequestVote(req: RequestVote): Future[RequestVoteResponse]
}

object Transport {

  import RestClient.implicits._

  case class RaftRestTransport(rest: RestClient)(implicit mat : Materializer) extends Transport {
    override def onAppendEntries[T: Encoder : Decoder](req: AppendEntries[T]): Future[AppendEntriesResponse] = {
      rest.send(RaftHttp.forAppend(req)).flatMap(_.as[AppendEntriesResponse])
    }

    override def onRequestVote(req: RequestVote): Future[RequestVoteResponse] = {
      rest.send(RaftHttp.forVote(req)).flatMap(_.as[RequestVoteResponse])
    }
  }

  def apply(rest: RestClient)(implicit mat : Materializer): Transport = new RaftRestTransport(rest)
}

trait Broadcast extends Transport {
  def nodeIds: Set[NodeId]
}

case class AppendEntries[T: Encoder : Decoder](from: NodeId,
                                               to: NodeId,
                                               term: Term,
                                               knownState: PeerState,
                                               entries: List[T],
                                               commitIndex: CommitIndex) extends RaftRequest {
  def prevIndex = knownState.matchIndex

  def prevTerm = knownState.term
}


case class AppendEntriesResponse(from: NodeId, to: NodeId, term: Term, success: Boolean, matchIndex: CommitIndex) extends RaftResponse

case class RequestVote(from: NodeId, to: NodeId, term: Term, lastLogIndex: CommitIndex, lastLogTerm: Term) extends RaftRequest

case class RequestVoteResponse(from: NodeId, to: NodeId, term: Term, granted: Boolean) extends RaftResponse
