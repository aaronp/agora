package miniraft.state

import java.nio.file.Path

import com.typesafe.scalalogging.StrictLogging
import miniraft._
import miniraft.state.Log.Formatter

import scala.collection.immutable

private[state] object RaftNodeLogic {
  def apply[T](id: NodeId, workingDir: Path)(applyToStateMachine: LogEntry[T] => Unit)(implicit fmt: Formatter[T, Array[Byte]]): RaftNodeLogic[T] = {
    val ps: PersistentState[T] = PersistentState[T](workingDir)(applyToStateMachine)
    apply(id, ps)
  }

  def apply[T](id: NodeId, ps: PersistentState[T]): RaftNodeLogic[T] = {
    new RaftNodeLogic[T](id, RaftState[T](ps))
  }

  def apply[T](id: NodeId, initialState: RaftState[T]): RaftNodeLogic[T] = {
    new RaftNodeLogic[T](id, initialState)
  }

}

private[state] class RaftNodeLogic[T](val id: NodeId, initialState: RaftState[T]) extends StrictLogging {
  private var state: RaftState[T] = initialState

  def raftState = state
  def raftState_=(newState: RaftState[T]) = {
    state = newState
  }

  def leaderId = raftState.persistentState.votedFor

  def leaderState = raftState.role match {
    case Leader(view) => Option(view)
    case _            => None
  }

  def isLeader = {
    raftState.role match {
      case _: Leader => true
      case _         => false
    }
  }

  def isCandidate = {
    raftState.role match {
      case _: Candidate => true
      case _            => false
    }
  }

  def isFollower = {
    raftState.role match {
      case Follower => true
      case _        => false
    }
  }

  def onElectionTimeout(cluster: ClusterProtocol): Unit = {
    raftState.role match {
      case _: Leader =>
        logger.warn(s"ignoring election timeout while already the leader w/ ${cluster}")
      case other =>
        logger.debug(s"Election timeout while in state ${other.name}, starting election...")

        raftState = raftState.becomeCandidate(cluster.clusterSize, id)
        cluster.electionTimer.reset()
        cluster.tellOthers(mkRequestVote)

    }
  }

  def mkRequestVote() = {
    RequestVote(currentTerm, id, lastCommittedIndex, lastLogTerm)
  }

  def currentTerm = raftState.currentTerm

  def lastCommittedIndex: LogIndex = raftState.lastCommittedIndex

  def lastUnappliedIndex: LogIndex = raftState.lastUnappliedIndex

  def lastLogTerm = raftState.lastLogTerm

  def onVoteRequest(vote: RequestVote, cluster: ClusterProtocol): RequestVoteResponse = {

    val granted = {
      val termOk = currentTerm <= vote.term

      val indexOk = vote.lastLogIndex >= lastCommittedIndex && vote.lastLogTerm >= lastLogTerm

      val weHaventVotedForAnyoneElseThisTerm = {
        val voteOk = raftState.persistentState.votedFor.fold(true) { otherId =>
          otherId == vote.candidateId
        }
        voteOk || currentTerm < vote.term
      }

      termOk && indexOk && weHaventVotedForAnyoneElseThisTerm
    }

    if (granted) {
      val newNode = raftState.voteFor(vote.term, vote.candidateId)
      raftState = newNode
      cluster.electionTimer.reset()
    }

    RequestVoteResponse(currentTerm, granted)
  }

  def onLeaderHeartbeatTimeout(cluster: ClusterProtocol) = {
    leaderState.foreach { _ =>
      val heartbeat = mkHeartbeatAppendEntries()
      cluster.tellOthers(heartbeat)
      cluster.heartbeatTimer.reset()
    }
  }

  def onAppendEntries(ae: AppendEntries[T], cluster: ClusterProtocol): AppendEntriesResponse = {
    val (newState, reply) = raftState.append(ae)
    cluster.electionTimer.reset()
    if (raftState.role.isLeader && !newState.role.isLeader) {
      cluster.heartbeatTimer.cancel()
    }
    raftState = newState
    reply
  }

  final def onRequest(req: RaftRequest, cluster: ClusterProtocol): RaftResponse = req match {
    case vote: RequestVote => onVoteRequest(vote, cluster)

    case ae: AppendEntries[T] => onAppendEntries(ae, cluster)
  }

  private def commitLogOnMajority(ourLatestIndex: LogIndex, newView: Map[NodeId, ClusterPeer], clusterSize: Int) = {
    val nodesWithMatchIndex = newView.values.map(_.matchIndex).count(_ == ourLatestIndex)
    if (isMajority(nodesWithMatchIndex, clusterSize)) {
      raftState.log.commit(ourLatestIndex)
    }
  }

  /** We're either the leader or have been at some point, as we just got an append entries response
    *
    * @param from
    * @param resp
    * @param cluster
    */
  def onAppendEntriesResponse(from: NodeId, resp: AppendEntriesResponse, cluster: ClusterProtocol): Unit = {

    for {
      view <- leaderState

      ourLatestLogIndex = lastCommittedIndex
    } {
      val newNode = if (resp.success) {

        // cool - let's update our view!
        // ... and do we need to send any updates?
        val lui       = lastUnappliedIndex
        val nextIndex = (resp.matchIndex + 1).min(lui)

        assert(resp.matchIndex <= ourLatestLogIndex, s"Match index '${resp.matchIndex}' from $from is >= our (leader's) largest index $ourLatestLogIndex")

        // do we need to send another append entries?
        if (resp.matchIndex < ourLatestLogIndex) {
          logEntryAt(nextIndex).foreach { entry =>
            cluster.tell(from, mkAppendEntries(nextIndex, entry))
          }
        }

        val newView = view.updated(from, ClusterPeer(resp.matchIndex, nextIndex))

        commitLogOnMajority(ourLatestLogIndex, newView, cluster.clusterSize)

        val updatedRole = Leader(newView)
        raftState.copy(role = updatedRole)
      } else {
        val nextIndex = view.get(from).map(_.nextIndex).getOrElse(resp.matchIndex)
        // the log index doesn't match .. we have to try with a lower 'next index'
//        val nextIndex = peerView.nextIndex - 1

        cluster.tell(from, mkHeartbeatAppendEntries(nextIndex))

        val updatedRole = Leader(view.updated(from, ClusterPeer(resp.matchIndex, nextIndex)))
        raftState.copy(role = updatedRole)
      }

      raftState = newNode
    }

  }

  private def onLeaderTransition(cluster: ClusterProtocol) = {
    logger.debug(s"${id} becoming leader")
    // TODO - confirm that our first append entries is the unapplied rather than committed index?
    cluster.tellOthers(mkHeartbeatAppendEntries())
    cluster.electionTimer.cancel()
    cluster.heartbeatTimer.reset()
  }

  def onRequestVoteResponse(from: NodeId, resp: RequestVoteResponse, cluster: ClusterProtocol): Unit = {
    raftState.role match {
      case Candidate(counter) =>
        counter.onVote(from, resp.granted) match {
          case Some(true) =>
            val newRole = counter.leaderRole(id, lastCommittedIndex)
            raftState = raftState.copy(role = newRole)
            onLeaderTransition(cluster)
          case Some(false) =>
            // we know now for definite that we won't become the leader.
            // hey ho ... let's just reset our timer and see how this all works out
            cluster.electionTimer.reset()
          case None => // nowt to do ... we don't have a definite result yet
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
      peer   <- leader.get(to)
      entry  <- logEntryAt(peer.nextIndex)
    } yield {
      entry
    }
  }

//  /** append the input command value and notify the rest of the cluster
//    * this only is applicable when we are the leader
//    *
//    * @param cluster the cluster view to notify
//    * @return true if we are the leader and have notified the cluster, false otherwise
//    */
//  def leaderApi(cluster: ClusterProtocol)(implicit ec: ExecutionContext): Option[LeaderApi[T]] = {
//    leaderState.map { _ =>
//      val api: LeaderApi[T] = LeaderApi[T]()
//
//      api
//    }
//  }

  def logEntryAt(index: LogIndex): Option[LogEntry[T]] = raftState.log.at(index)

  def logsEntriesBetween(from: LogIndex, to: LogIndex = lastUnappliedIndex): immutable.IndexedSeq[LogEntry[T]] = {
    (from to to).flatMap(logEntryAt)
  }

  def mkAppendEntries(prevIdx: LogIndex, entry: LogEntry[T]): AppendEntries[T] = {
    val ae = AppendEntries(
      term = currentTerm,
      leaderId = id,
      commitIndex = entry.index,
      prevLogIndex = prevIdx,
      prevLogTerm = entry.term,
      entry = Option(entry.command)
    )

    ae
  }

  def mkHeartbeatAppendEntries(prevIdx: LogIndex = lastUnappliedIndex): AppendEntries[T] = {
    AppendEntries(
      term = currentTerm,
      leaderId = id,
      commitIndex = raftState.lastCommittedIndex,
      prevLogIndex = prevIdx,
      prevLogTerm = raftState.lastLogTerm,
      entry = None
    )
  }

  final def onResponse(from: NodeId, resp: RaftResponse, cluster: ClusterProtocol): Unit = resp match {
    case resp: RequestVoteResponse   => onRequestVoteResponse(from, resp, cluster)
    case resp: AppendEntriesResponse => onAppendEntriesResponse(from, resp, cluster)
  }

  override def toString = {
    s"------------------------ Node $id ------------------------\n${pprint.stringify(raftState)}"
  }

  override def equals(other: Any) = other match {
    case ss: RaftNodeLogic[T] => id == ss.id
    case _                    => false
  }

  override def hashCode = 17 * id.hashCode

}
