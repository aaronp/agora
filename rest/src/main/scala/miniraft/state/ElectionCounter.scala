package miniraft.state

private[state] class ElectionCounter(val clusterSize: Int) {

  override def toString = s"CandidateCtr(${votesFor.mkString("for: [", ",", "]")}, ${votesAgainst.mkString("against:[", ",", "]")} of $clusterSize"

  private object Lock

  override def hashCode = clusterSize * 37

  override def equals(other: Any) = other match {
    case c: ElectionCounter if c.clusterSize == clusterSize =>
      c.votesFor == votesFor && c.votesAgainst == votesAgainst
    case _ => false
  }

  private var votesFor = Set[NodeId]()
  private var votesAgainst = Set[NodeId]()

  def votedFor = votesFor

  def receivedVotesFrom = votesFor ++ votesAgainst

  def leaderRole(forNode: NodeId): Leader = {
    require(isMajority(votesFor.size, clusterSize), s"Asked to make a leader role on a cluster of size ?$clusterSize w/ $votesFor")
    val view = receivedVotesFrom.withFilter(_ != forNode).map(_ -> ClusterPeer.empty).toMap
    Leader(view)
  }

  def onVote(nodeId: NodeId, granted: Boolean): Option[Boolean] = Lock.synchronized {
    if (granted) {
      require(!votesAgainst.contains(nodeId), s"We already had a vote from $nodeId")
      require(!votesFor.contains(nodeId), s"We already had a vote from $nodeId")
      votesFor = votesFor + nodeId
      if (isMajority(votesFor.size, clusterSize)) {
        Option(true)
      } else {
        None
      }
    } else {
      require(!votesAgainst.contains(nodeId), s"We already had a vote from $nodeId")
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