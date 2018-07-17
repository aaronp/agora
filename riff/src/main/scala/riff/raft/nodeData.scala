package riff.raft

trait HasNodeData {
  def data: NodeData

  def leaderOpinion: LeaderOpinion = data.leaderOpinion

  def currentTerm: Int = data.currentTerm

  def name: String = data.name

  def uncommittedLogIndex: Int = data.uncommittedLogIndex

  def peersByName: Map[String, Peer] = data.peersByName

  final def voteCount() = peersByName.values.count(_.voteGranted)
}


final case class NodeData(override val leaderOpinion: LeaderOpinion,
                          override val uncommittedLogIndex: Int,
                          override val peersByName: Map[String, Peer]) extends HasNodeData {
  require(currentTerm > 0)

  override def currentTerm: Int = leaderOpinion.term

  override def data: NodeData = this

  override def name: String = leaderOpinion.name

  def withPeers(peers: Map[String, Peer]): NodeData = copy(peersByName = peers)

  def withPeers(peers: Peer*): NodeData = withPeers(peers.map(p => p.name -> p).toMap)

  def incTerm: NodeData = updated(newLeaderOpinion = LeaderOpinion.ImACandidate(name, currentTerm + 1))

  def updated(newLeaderOpinion: LeaderOpinion = leaderOpinion, newIndex: Int = uncommittedLogIndex): NodeData = {
    copy(leaderOpinion = newLeaderOpinion, uncommittedLogIndex = newIndex)
  }

}

object NodeData {

  def apply(name: String): NodeData = new NodeData(
    leaderOpinion = LeaderOpinion.Unknown(name, 1),
    uncommittedLogIndex = 0,
    peersByName = Map.empty
  )
}
