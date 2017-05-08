package miniraft.state


trait Transport {
  def tell(id: NodeId, raftRequest: RaftRequest): Unit

  def tellOthers(raftRequest: RaftRequest): Unit
}