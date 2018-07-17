package riff.raft

/**
  * Represents a follower, candidate or leader node
  */
sealed trait RaftNode extends HasNodeData {

  type Self <: RaftNode

  def role: NodeRole

  def isLeader = role == NodeRole.Leader

  def isCandidate = role == NodeRole.Candidate

  def isFollower = role == NodeRole.Follower

  protected def withData(newData: NodeData): Self

  def withPeers(peers: Map[String, Peer]): Self = withData(data.withPeers(peers))

  def withPeers(peers: Peer*): Self = withPeers(peers.map(p => p.name -> p).toMap)

  final def clusterSize: Int = peersByName.size + 1

  final def clusterNodes: Set[String] = peersByName.keySet + name

  def onAppendEntries[T](append: AppendEntries[T]): (RaftNode, AppendEntriesReply)

  def onRequestVoteReply(requestVote: RequestVoteReply, logState : CommitLogState): RaftNode = this

  def onRequestVote(requestVote: RequestVote): (RaftNode, RequestVoteReply) = {
    def reply(ok: Boolean) = RequestVoteReply(name, requestVote.from, requestVote.term, ok)

    if (requestVote.term > currentTerm) {
      FollowerNode(data.updated(newLeaderOpinion = LeaderOpinion.TheLeaderIs(requestVote.from, requestVote.term)), votedFor = Option(requestVote.from)) -> reply(true)
    } else {
      this -> reply(false)
    }
  }

  protected def mkAppendReply[T](append: AppendEntries[T], ok: Boolean): AppendEntriesReply = {
    AppendEntriesReply(name, append.from, currentTerm, ok, matchIndex = uncommittedLogIndex + 1)
  }

  override def toString: String = format(this)
}


final case class FollowerNode(override val data: NodeData, votedFor: Option[String]) extends RaftNode {


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
    * @tparam T
    * @return
    */
  override def onAppendEntries[T](append: AppendEntries[T]): (FollowerNode, AppendEntriesReply) = {

    currentTerm match {
      case t if t == append.term =>
        this -> mkAppendReply(append, append.prevIndex == uncommittedLogIndex)
      case t if t < append.term =>
        // we're out-of-sync
        copy(data = data.updated(newLeaderOpinion = LeaderOpinion.TheLeaderIs(append.from, append.term)), votedFor = None) -> mkAppendReply(append, false)
      case _ => // ignore
        this -> mkAppendReply(append, false)
    }
  }

  override def onRequestVote(requestFromCandidate: RequestVote): (FollowerNode, RequestVoteReply) = {
    def reply(ok: Boolean) = RequestVoteReply(from = name, to = requestFromCandidate.from, term = currentTerm, granted = ok)

    votedFor match {
      case _ if requestFromCandidate.term > currentTerm =>
        val newState = copy(data = data.updated(newLeaderOpinion = LeaderOpinion.TheLeaderIs(requestFromCandidate.from, requestFromCandidate.term)),
          votedFor = Option(requestFromCandidate.from))
        (newState, reply(true))

      case None if requestFromCandidate.term == currentTerm && votedFor.isEmpty =>
        val newState = copy(data = data.updated(newLeaderOpinion = LeaderOpinion.TheLeaderIs(requestFromCandidate.from, requestFromCandidate.term)),
          votedFor = Option(requestFromCandidate.from))
        (newState, reply(true))
      case _ => (this, reply(false))
    }
  }

  def onElectionTimeout(): CandidateNode = CandidateNode(data.incTerm)

}

final case class CandidateNode(override val data: NodeData) extends RaftNode {
  override val role: NodeRole = NodeRole.Candidate
  override val isCandidate = true

  override def onAppendEntries[T](append: AppendEntries[T]): (RaftNode, AppendEntriesReply) = {

    currentTerm match {
      case t if t == append.term =>
        this -> mkAppendReply(append, append.commitIndex == uncommittedLogIndex)
      case t if t < append.term =>
        // we're out-of-sync
        val newNode = FollowerNode(data.updated(newLeaderOpinion = LeaderOpinion.TheLeaderIs(append.from, append.term)), votedFor = None)
        newNode -> mkAppendReply(append, false)
      case _ => // ignore appends before us
        this -> mkAppendReply(append, false)
    }
  }

  override def onRequestVoteReply(reply: RequestVoteReply, logState : CommitLogState): RaftNode = {
    peersByName.get(reply.from).fold(this: RaftNode) { peer =>
      if (reply.term == currentTerm) {
        val newPeer = peer.copy(voteGranted = reply.granted)

        val newTotalVotes = voteCount() + 1 + 1
        val newPeers = peersByName.updated(reply.from, newPeer)
        if (CandidateNode.hasQuorum(newTotalVotes, clusterSize)) {
          LeaderNode(data.copy(peersByName = newPeers))
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

final case class LeaderNode(override val data: NodeData) extends RaftNode {
  def onSendHeartbeatTimeout(now : Long): LeaderNode = {
    val newPeers = data.peersByName.filter {
      case (_, peer) => peer.requiresHeartbeat(now)
    }
    if (newPeers.isEmpty) {
      this
    } else {

    }
    withPeers(newPeers)
  }


  def logCoords = LogCoords(currentTerm, uncommittedLogIndex)

  def makeAppend[T](data : T, logState : CommitLogState): (LeaderNode, Seq[AppendEntries[T]]) = {
    val appends = peersByName.values.collect {
      case peer if peer.matchIndex == logState.commitIndex =>
        AppendEntries[T](name, peer.name, currentTerm, logState.prevLogIndex, logState.prevLogTerm, data, logState.commitIndex)
    }
    val myAppend = AppendEntries[T](name, name, currentTerm, logState.prevLogIndex, logState.prevLogTerm, data, logState.commitIndex)
    val entries = myAppend +: appends.toSeq


    ???
  }

  def onAppendEntriesReply(reply: AppendEntriesReply): LeaderNode = {
    if (reply.to != name) {
      this
    } else {
      //if we have consensus on reply, we can commit
      updatePeer(reply.from)(_.copy(nextIndex = reply.matchIndex + 1, matchIndex = reply.matchIndex))
    }
  }

  override val role: NodeRole = NodeRole.Leader

  override val isLeader = true

  override def onAppendEntries[T](append: AppendEntries[T]): (RaftNode, AppendEntriesReply) = {

    currentTerm match {
      case t if t < append.term =>
        // we're out-of-sync
        val newNode = FollowerNode(data.updated(newLeaderOpinion = LeaderOpinion.TheLeaderIs(append.from, append.term)), votedFor = None)
        newNode -> mkAppendReply(append, false)
      case _ => // ignore appends before us
        this -> mkAppendReply(append, false)
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

  def apply(nodeName: String): FollowerNode = FollowerNode(NodeData(nodeName), None)
}