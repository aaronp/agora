package miniraft.state

class ElectionCounter(val clusterSize: Int, initialFor: Set[NodeId] = Set.empty, initialAgainst: Set[NodeId] = Set.empty) {

  override def toString = s"${votesFor.mkString("for: [", ",", "]")}, ${votesAgainst.mkString("against:[", ",", "]")} of $clusterSize"

  private object Lock

  override def hashCode = clusterSize * 37

  override def equals(other: Any) = other match {
    case c: ElectionCounter if c.clusterSize == clusterSize =>
      c.votesFor == votesFor && c.votesAgainst == votesAgainst
    case _ => false
  }

  private var votesFor     = initialFor
  private var votesAgainst = initialAgainst

  def votedFor: Set[NodeId]     = votesFor
  def votedAgainst: Set[NodeId] = votesAgainst

  def receivedVotesFrom = votesFor ++ votesAgainst

  def leaderRole(forNode: NodeId, nextIndex: Int): Leader = {
    require(isMajority(votesFor.size, clusterSize), s"Asked to make a leader role on a cluster of size ?$clusterSize w/ $votesFor")
    val view = receivedVotesFrom.withFilter(_ != forNode).map(_ -> ClusterPeer.empty(nextIndex)).toMap
    Leader(view)
  }

  def onVote(nodeId: NodeId, granted: Boolean): Option[Boolean] = Lock.synchronized {
    if (granted) {
      require(!votesAgainst.contains(nodeId), s"We already had a vote against from $nodeId")
      require(!votesFor.contains(nodeId), s"We already had a vote for from $nodeId")
      votesFor = votesFor + nodeId
      if (isMajority(votesFor.size, clusterSize)) {
        Option(true)
      } else {
        None
      }
    } else {
      require(!votesAgainst.contains(nodeId), s"We already had a vote against from $nodeId")
      require(!votesFor.contains(nodeId), s"We already had a vote from $nodeId")
      votesAgainst = votesAgainst + nodeId
      if (isMajority(votesAgainst.size, clusterSize)) {
        Option(false)
      } else {
        None
      }
    }
  }
}
