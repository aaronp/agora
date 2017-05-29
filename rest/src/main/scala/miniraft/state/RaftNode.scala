package miniraft.state

import java.nio.file.Path

import miniraft.state.Log.Formatter

import scala.concurrent.Future


object RaftNode {
  def apply[T](id: NodeId, workingDir: Path)(implicit fmt: Formatter[T, Array[Byte]]) = {
    val ps = PersistentState[T](workingDir)
    new RaftNode[T](id, RaftState[T](ps))
  }

  def apply[T](id: NodeId, initialState: RaftState[T]) = {
    new RaftNode[T](id, initialState)
  }
}

class RaftNode[T](val id: NodeId, initialState: RaftState[T]) {

  private class AsEndpoint(cluster: ClusterProtocol) extends RaftEndpoint[T] {

    override def onVote(vote: RequestVote) = Future.successful(onVoteRequest(vote, cluster))

    override def onAppend(append: AppendEntries[T]) = Future.successful(onAppendEntries(append, cluster))
  }

  def endpoint(cluster: ClusterProtocol): RaftEndpoint[T] = new AsEndpoint(cluster)

  private var node: RaftState[T] = initialState

  def raftNode = node

  def leaderId = raftNode.persistentState.votedFor

  private def raftNode_=(newNode: RaftState[T]) = {
    node = newNode
  }

  def leaderState = raftNode.role match {
    case Leader(view) => Option(view)
    case _ => None
  }


  def onElectionTimeout(cluster: ClusterProtocol): Unit = {
    raftNode = node.becomeCandidate(cluster.clusterSize, id)
    cluster.tellOthers(mkRequestVote)
    cluster.electionTimer.reset()
  }

  def mkRequestVote() = {
    RequestVote(currentTerm, id, lastCommittedIndex, lastLogTerm)
  }

  def currentTerm = raftNode.currentTerm

  def lastCommittedIndex: LogIndex = raftNode.lastCommittedIndex

  def lastUnappliedIndex: LogIndex = raftNode.lastUnappliedIndex

  def lastLogTerm = raftNode.lastLogTerm

  def onVoteRequest(vote: RequestVote, cluster: ClusterProtocol): RequestVoteResponse = {

    val granted = {
      val termOk = currentTerm <= vote.term


      val indexOk = vote.lastLogIndex >= lastCommittedIndex && vote.lastLogTerm >= lastLogTerm

      val weHaventVotedForAnyoneElseThisTerm = {
        val voteOk = raftNode.persistentState.votedFor.fold(true) { otherId =>
          otherId == vote.candidateId
        }
        voteOk || currentTerm < vote.term
      }

      termOk && indexOk && weHaventVotedForAnyoneElseThisTerm
    }

    if (granted) {
      val newNode = raftNode.voteFor(vote.term, vote.candidateId)
      raftNode = newNode
      cluster.electionTimer.reset()
    }

    RequestVoteResponse(currentTerm, granted)
  }


  def onLeaderHeartbeatTimeout(cluster: ClusterProtocol) = {
    leaderState.foreach {
      _ =>
        val heartbeat = mkAppendEntries(lastCommittedIndex, Nil)
        cluster.tellOthers(heartbeat)
        cluster.heartbeatTimer.reset()
    }
  }

  def onAppendEntries(ae: AppendEntries[T], cluster: ClusterProtocol): AppendEntriesResponse = {
    val (newState, reply) = raftNode.append(ae)
    cluster.electionTimer.reset()
    raftNode = newState
    reply
  }

  final def onRequest(req: RaftRequest, cluster: ClusterProtocol): RaftResponse = req match {
    case vote: RequestVote => onVoteRequest(vote, cluster)
    case ae: AppendEntries[T] => onAppendEntries(ae, cluster)
  }

  private def commitLogOnMajority(ourLatestIndex: LogIndex, newView: Map[NodeId, ClusterPeer], clusterSize: Int) = {
    val nodesWithMatchIndex = newView.values.map(_.matchIndex).count(_ == ourLatestIndex)
    if (isMajority(nodesWithMatchIndex, clusterSize)) {
      raftNode.log.commit(ourLatestIndex)
    }
  }

  def onAppendEntriesResponse(from: NodeId, resp: AppendEntriesResponse, cluster: ClusterProtocol): Unit = {
    // ooh! We must be the leader... maybe. At least we must've been at some point to
    // have sent out an append entries request
    for {
      view <- leaderState
      peerView <- view.get(from)
      ourLatestLogIndex = lastCommittedIndex
    } {
      val newNode = if (resp.success) {

        // cool - let's update our view!
        // ... and do we need to send any updates?
        val nextIndex = resp.matchIndex + 1

        assert(resp.matchIndex <= ourLatestLogIndex, s"Match index '${
          resp.matchIndex
        }' from $from is >= our (leader's) largest index $ourLatestLogIndex")

        // do we need to send another append entries?
        if (resp.matchIndex < ourLatestLogIndex) {
          mkLogEntry(nextIndex).foreach {
            entry =>
              cluster.tell(from, mkAppendEntries(nextIndex, entry :: Nil))
          }
        }

        val newView = view.updated(from, ClusterPeer(resp.matchIndex, nextIndex))

        commitLogOnMajority(ourLatestLogIndex, newView, cluster.clusterSize)

        val updatedRole = Leader(newView)
        raftNode.copy(role = updatedRole)
      } else {
        // the log index doesn't match .. we have to try with a lower 'next index'
        val nextIndex = peerView.nextIndex - 1

        cluster.tell(from, mkAppendEntries(nextIndex, Nil))

        val updatedRole = Leader(view.updated(from, ClusterPeer(resp.matchIndex, nextIndex)))
        raftNode.copy(role = updatedRole)
      }

      raftNode = newNode
    }

  }

  private def becomeLeader(resp: RequestVoteResponse, cluster: ClusterProtocol, newRole: Leader) = {
    val newNode = raftNode.copy(role = newRole)
    raftNode = newNode
    cluster.tellOthers(mkAppendEntries(lastUnappliedIndex, Nil))
    cluster.electionTimer.cancel()
    cluster.heartbeatTimer.reset()
  }

  def onRequestVoteResponse(from: NodeId, resp: RequestVoteResponse, cluster: ClusterProtocol): Unit = {
    raftNode.role match {
      case Candidate(counter) =>
        counter.onVote(from, resp.granted) match {
          case Some(true) => becomeLeader(resp, cluster, counter.leaderRole(id))
          case _ =>
        }
      case _ =>
    }
  }

  /** @param to the node to send the entry to
    * @return the LogEntry to send, if
    *         (1) we're the leader,
    *         (2) we know about the peer 'to',
    *         (3) an entry exists at the peer's match index
    */
  def mkLogEntry(to: NodeId): Option[LogEntry[T]] = {
    for {
      leader <- leaderState
      peer <- leader.get(to)
      entry <- mkLogEntry(peer.nextIndex)
    } yield {
      entry
    }
  }

  def mkLogEntry(index: LogIndex) = raftNode.log.at(index).map {
    entry =>
      LogEntry[T](currentTerm, index, entry.command)
  }

  def mkAppendEntries(idx: LogIndex, newEntries: List[LogEntry[T]]): AppendEntries[T] = {
    val rn = raftNode
    AppendEntries(
      term = currentTerm,
      leaderId = id,
      prevLogIndex = idx,
      prevLogTerm = lastLogTerm,
      entries = newEntries,
      leaderCommit = rn.lastCommittedIndex
    )
  }

  final def onResponse(from: NodeId, resp: RaftResponse, cluster: ClusterProtocol): Unit = resp match {
    case resp: RequestVoteResponse => onRequestVoteResponse(from, resp, cluster)
    case resp: AppendEntriesResponse => onAppendEntriesResponse(from, resp, cluster)
  }

  override def toString = {
    s"------------------------ Node $id ------------------------\n${
      pprint.stringify(raftNode)
    }"
  }

  override def equals(other: Any) = other match {
    case ss: RaftNode[T] => id == ss.id
    case _ => false
  }

  override def hashCode = 17 * id.hashCode

}