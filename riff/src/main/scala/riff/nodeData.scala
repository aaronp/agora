package riff


trait HasNodeData {
  def data: NodeData

  def currentTerm: Int = data.currentTerm

  def commitIndex: Int = data.commitIndex

  def peersByName: Map[String, Peer] = data.peersByName

  final def voteCount() = peersByName.values.count(_.voteGranted)
}


final case class NodeData(override val currentTerm: Int,
                          override val commitIndex: Int,
                          override val peersByName: Map[String, Peer]) extends HasNodeData {
  require(currentTerm > 0)

  override def data: NodeData = this

  def withPeers(peers: Map[String, Peer]): NodeData = copy(peersByName = peers)

  def withPeers(peers: Peer*): NodeData = withPeers(peers.map(p => p.name -> p).toMap)

  def inc: NodeData = updated(newTerm = currentTerm + 1)

  def updated(newTerm: Int = currentTerm, newIndex: Int = commitIndex): NodeData = copy(currentTerm = newTerm, commitIndex = newIndex)

}

object NodeData {

  def apply(): NodeData = new NodeData(
    currentTerm = 1,
    commitIndex = 0,
    peersByName = Map.empty
  )
}
