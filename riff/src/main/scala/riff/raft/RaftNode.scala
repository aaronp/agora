package riff.raft

import riff._

import scala.collection.immutable

/**
  * Represents a follower, candidate or leader node
  */
sealed trait RaftNode extends HasNodeData {

  type Self <: RaftNode

  def name: String

  def role: NodeRole

  def isLeader = role == NodeRole.Leader

  def isCandidate = role == NodeRole.Candidate

  def isFollower = role == NodeRole.Follower

  protected def withData(newData: NodeData): Self

  def withPeers(peers: Map[String, Peer]): Self = withData(data.withPeers(peers))

  def withPeers(peers: Peer*): Self = withPeers(peers.map(p => p.name -> p).toMap)

  def updated(term: Int, newCommitIndex: Int = commitIndex): Self = withData(data.copy(currentTerm = term, commitIndex = newCommitIndex))

  def onAppendEntries[T](append: AppendEntries[T], now: Long): (RaftNode, AppendEntriesReply)

  def onRequestVoteReply(requestVote: RequestVoteReply, now: Long): RaftNode = this

  def onRequestVote(requestVote: RequestVote, now: Long): (RaftNode, RequestVoteReply) = {
    def reply(ok: Boolean) = RequestVoteReply(name, requestVote.from, now, requestVote.term, ok)

    if (requestVote.term > currentTerm) {
      FollowerNode(name, data.updated(newTerm = requestVote.term), votedFor = Option(requestVote.from)) -> reply(true)
    } else {
      this -> reply(false)
    }
  }

  protected def mkAppendReply[T](append: AppendEntries[T], now: Long, ok: Boolean): AppendEntriesReply = {
    AppendEntriesReply(name, append.from, now, currentTerm, ok, matchIndex = commitIndex)
  }

  override def toString: String = format(this)
}


/**
  * @param name
  */
final case class FollowerNode(name: String, override val data: NodeData, votedFor: Option[String]) extends RaftNode {


  override val role: NodeRole = NodeRole.Follower
  override val isFollower = true
  override type Self = FollowerNode

  protected def withData(newData: NodeData): FollowerNode = copy(data = newData)


  /**
    * Append entries to this node, using the given time as a reply.
    *
    * The request will succeed if it is for the same term and expected commit index.
    *
    * If the request is for an older term it will be ignored
    *
    * If it is for a newer term then this node will assume there is a new leader (one for which it may not have voted)
    * and update its term.
    *
    * @param append
    * @param now
    * @tparam T
    * @return
    */
  override def onAppendEntries[T](append: AppendEntries[T], now: Long): (FollowerNode, AppendEntriesReply) = {

    currentTerm match {
      case t if t == append.term =>
        this -> mkAppendReply(append, now, append.commitIndex == commitIndex)
      case t if t < append.term =>
        // we're out-of-sync
        copy(data = data.updated(newTerm = append.term), votedFor = None) -> mkAppendReply(append, now, false)
      case _ => // ignore
        this -> mkAppendReply(append, now, false)
    }
  }

  override def onRequestVote(requestFromCandidate: RequestVote, now: Long): (FollowerNode, RequestVoteReply) = {
    def reply(ok: Boolean) = RequestVoteReply(from = name, to = requestFromCandidate.from, sent = now, term = currentTerm, granted = ok)

    votedFor match {
      case _ if requestFromCandidate.term > currentTerm =>
        val newState = copy(data = data.updated(newTerm = requestFromCandidate.term), votedFor = Option(requestFromCandidate.from))
        (newState, reply(true))

      case None if requestFromCandidate.term == currentTerm && votedFor.isEmpty =>
        val newState = copy(data = data.updated(newTerm = requestFromCandidate.term), votedFor = Option(requestFromCandidate.from))
        (newState, reply(true))
      case _ => (this, reply(false))
    }
  }

  def onElectionTimeout(now: Long): CandidateNode = CandidateNode(name, data.inc, now)
}

final case class CandidateNode(name: String, override val data: NodeData, electionTimedOutAt: Long) extends RaftNode {
  override val role: NodeRole = NodeRole.Candidate
  override val isCandidate = true

  def clusterSize = peersByName.size + 1

  override def onAppendEntries[T](append: AppendEntries[T], now: Long): (RaftNode, AppendEntriesReply) = {

    currentTerm match {
      case t if t == append.term =>
        this -> mkAppendReply(append, now, append.commitIndex == commitIndex)
      case t if t < append.term =>
        // we're out-of-sync
        FollowerNode(name, data.updated(newTerm = append.term), votedFor = None) -> mkAppendReply(append, now, false)
      case _ => // ignore appends before us
        this -> mkAppendReply(append, now, false)
    }
  }

  override def onRequestVoteReply(reply: RequestVoteReply, now: Long): RaftNode = {
    peersByName.get(reply.from).fold(this: RaftNode) { peer =>
      if (reply.term == currentTerm) {
        val newPeer = peer.copy(voteGranted = reply.granted)

        val newTotalVotes = voteCount() + 1 + 1
        val newPeers = peersByName.updated(reply.from, newPeer)
        if (CandidateNode.hasQuorum(newTotalVotes, clusterSize)) {
          LeaderNode(name, data.copy(peersByName = newPeers))
        } else {
          withPeers(newPeers)
        }
      } else {
        // got a delinquent vote response from some other timeout - !
        // ignore it
        this
      }
    }
  }

  override type Self = CandidateNode

  protected def withData(newData: NodeData): CandidateNode = copy(data = newData)

}

object CandidateNode {
  def hasQuorum(totalVotes: Int, clusterSize: Int): Boolean = {
    require(clusterSize > 0)
    val requiredVotes = clusterSize / 2
    totalVotes > requiredVotes
  }
}

final case class LeaderNode(name: String, override val data: NodeData) extends RaftNode {
  def onAppendEntriesReply(reply: AppendEntriesReply): LeaderNode = {
    if (reply.to != name) {
      this
    } else {
      updatePeer(reply.from)(_.copy(nextIndex = reply.matchIndex + 1, matchIndex = reply.matchIndex))
    }
  }

  override val role: NodeRole = NodeRole.Leader

  override val isLeader = true

  /**
    * This should be called *after* some data T has been written to the uncommitted journal.
    *
    * We should thus still have the same commit index until we get the acks back
    *
    * @param entries
    * @tparam T
    * @return
    */
  def append[T](entries: T): immutable.Iterable[AppendEntries[T]] = {
    peersByName.collect {
      case (_, peer) if peer.matchIndex == commitIndex =>
        AppendEntries[T](
          from = name,
          to = peer.name,
          term = currentTerm,
          prevIndex = peer.matchIndex,
          prevTerm = currentTerm,
          entries = entries,
          commitIndex = commitIndex)
    }
  }


  override def onAppendEntries[T](append: AppendEntries[T], now: Long): (RaftNode, AppendEntriesReply) = {

    currentTerm match {
      case t if t < append.term =>
        // we're out-of-sync
        FollowerNode(name, data.updated(newTerm = append.term), votedFor = None) -> mkAppendReply(append, now, false)
      case _ => // ignore appends before us
        this -> mkAppendReply(append, now, false)
    }
  }

  private def updatePeer(name: String)(f: Peer => Peer): Self = {
    peersByName.get(name).fold(this) { peer =>
      val newPeer: Peer = f(peer)
      val newPeers = peersByName.updated(name, newPeer)
      withPeers(newPeers)
    }
  }

  override type Self = LeaderNode

  protected def withData(newData: NodeData): LeaderNode = copy(data = newData)
}


object RaftNode {

  def apply(nodeName: String): FollowerNode = FollowerNode(name = nodeName, data = NodeData(), None)
}