package miniraft.state


class ServerState(val id: NodeId) {

  // this server's known view of the cluster. This could be moved to be known by the log contained by the raft node
  var clusterView = Set[NodeId]()

  // the node state
  private var node: RaftNode = RaftNode()

  def raftNode = node

  var leaderState: Option[LeaderState] = None

  def onElectionTimeout(t: Transport): Unit = {
???
  }

  def onVoteRequest(vote: RequestVote): RequestVoteResponse = {
    ???
  }

  def onAppendEntries(ae: AppendEntries): AppendEntriesResponse = {
    ???
  }

  final def receive(req: RaftRequest): RaftResponse = req match {
    case vote: RequestVote => onVoteRequest(vote)
    case ae: AppendEntries => onAppendEntries(ae)
  }

  def onAppendEntriesResponse(resp: AppendEntriesResponse): Unit = {
    ???
  }

  def onRequestVoteResponse(resp: RequestVoteResponse): Unit = {
    ???
  }

  final def onResponse(resp: RaftResponse): Unit = resp match {
    case resp: RequestVoteResponse => onRequestVoteResponse(resp)
    case resp: AppendEntriesResponse => onAppendEntriesResponse(resp)
  }


  def withNode(n: RaftNode) = {
    node = n
    this
  }
}