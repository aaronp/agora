package riff

sealed trait RaftNode {
  def name: String

  def state: RaftState
}

trait HasNodeData {
  def data: NodeData

  def currentTerm: Int = data.currentTerm

  def votedFor: Option[String] = data.votedFor

  def commitIndex: Int = data.commitIndex

  def peersByName: Map[String, Peer] = data.peersByName
}

case class NodeData(override val currentTerm: Int,
                    override val votedFor: Option[String],
                    override val commitIndex: Int,
                    override val peersByName: Map[String, Peer]) extends HasNodeData {
  require(currentTerm > 0)

  override def data = this

  def electionTimeout(votedForName: String): NodeData = {
    copy(currentTerm = currentTerm + 1,
      votedFor = Option(votedForName))
  }

}

object NodeData {

  def initial = NodeData(
    currentTerm = 1,
    votedFor = None,
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
final case class FollowerNode(name: String, override val data: NodeData) extends RaftNode with HasNodeData {
  override val state: RaftState = RaftState.Follower

  def onElectionTimeout(now: Long): CandidateNode = CandidateNode(name, data.electionTimeout(name), now)

  def withPeers(peers: Map[String, Peer]): FollowerNode = copy(data = data.copy(peersByName = peers))
}

final case class CandidateNode(name: String, override val data: NodeData, electionTimedOutAt: Long) extends RaftNode with HasNodeData {
  override val state: RaftState = RaftState.Candidate
}

final case class LeaderNode(name: String, override val data: NodeData) extends RaftNode with HasNodeData {
  override val state: RaftState = RaftState.Leader

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

  def initial(nodeName: String) = FollowerNode(name = nodeName, data = NodeData.initial)
}