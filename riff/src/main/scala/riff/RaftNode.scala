package riff

sealed trait RaftNode {
  def name: String

  def state: RaftState
}

trait HasNodeData {
  def data: NodeData

  def currentTerm: Int = data.currentTerm

  def commitIndex: Int = data.commitIndex

  def peersByName: Map[String, Peer] = data.peersByName
}

case class NodeData(override val currentTerm: Int,
                    override val commitIndex: Int,
                    override val peersByName: Map[String, Peer]) extends HasNodeData {
  require(currentTerm > 0)

  override def data = this

  def inc : NodeData = copy(currentTerm = currentTerm + 1)

}

object NodeData {

  def initial = NodeData(
    currentTerm = 1,
    commitIndex = 0,
    peersByName = Map.empty
  )
}

/**
  * TODO - consider making this a sealed trait /w different properties on
  * follower, candidate and leader
  *
  * @param name
  */
final case class FollowerNode(name: String, override val data: NodeData, votedFor: Option[String]) extends RaftNode with HasNodeData {
  def onRequestVote(requestFromCandidate: RequestVote, now : Long): (FollowerNode, RequestVoteReply) = {
    votedFor match {
      case None if requestFromCandidate.term > currentTerm =>
        val newState = copy(data = data.inc, votedFor = Option(requestFromCandidate.from))
        val reply = RequestVoteReply(from = name, to = requestFromCandidate.from, sent = now, term = currentTerm, granted = true)

        (newState, reply)
      case _ =>
        val newState = this
        val reply = RequestVoteReply(from = name, to = requestFromCandidate.from, sent = now, term = currentTerm, granted = false)

        (newState, reply)
    }
  }

  override val state: RaftState = RaftState.Follower

  def onElectionTimeout(now: Long): CandidateNode = CandidateNode(name, data.inc, now)

  def withPeers(peers: Map[String, Peer]): FollowerNode = copy(data = data.copy(peersByName = peers))
  def withTerm(term: Int): FollowerNode = copy(data = data.copy(currentTerm = term))

}

final case class CandidateNode(name: String, override val data: NodeData, electionTimedOutAt: Long) extends RaftNode with HasNodeData {
  override val state: RaftState = RaftState.Candidate
  def votedFor = name
}

final case class LeaderNode(name: String, override val data: NodeData) extends RaftNode with HasNodeData {
  override val state: RaftState = RaftState.Leader
  def votedFor = name
  /**
    * This should be called *after* some data T has been written to the uncommitted journal.
    *
    * We should thus still have the same commit index until we get the acks back
    *
    * @param entries
    * @tparam T
    * @return
    */
  def append[T](entries: T) = {
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
}


object RaftNode {

  def initial(nodeName: String) = FollowerNode(name = nodeName, data = NodeData.initial, None)
}