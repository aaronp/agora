package miniraft.state

import java.nio.file.Path

import com.typesafe.scalalogging.StrictLogging
import miniraft._
import miniraft.state.Log.Formatter

import scala.collection.immutable
import scala.concurrent.ExecutionContext

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

  private var pendingAppendAcks: List[UpdateResponse.Appendable] = Nil

  def pendingLeaderAcks = pendingAppendAcks

  private[state] def add(command: T, protocol: ClusterProtocol)(implicit ec: ExecutionContext): UpdateResponse.Appendable = {
    val matchIndex = lastUnappliedIndex
    val index      = matchIndex + 1

    val appendEntries: AppendEntries[T] = {
      val prevTerm = raftState.lastLogTerm
      val myEntry  = LogEntry[T](currentTerm, index, command)
      raftState.log.append(myEntry)
      mkAppendEntries(lastCommittedIndex, matchIndex, prevTerm, command)
    }

    val clientAppendableResponse: UpdateResponse.Appendable = UpdateResponse(protocol.clusterNodeIds, index)
    // we can respond to our append immediately
    clientAppendableResponse.onResponse(id, AppendEntriesResponse(appendEntries.term, true, index))

    pendingAppendAcks = clientAppendableResponse :: pendingAppendAcks
    protocol.tellOthers(appendEntries)
    clientAppendableResponse
  }

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

  private def mkRequestVote() = RequestVote(currentTerm, id, lastCommittedIndex, lastLogTerm)

  def currentTerm = raftState.currentTerm

  def lastCommittedIndex: LogIndex = raftState.lastCommittedIndex

  def lastUnappliedIndex: LogIndex = raftState.lastUnappliedIndex

  def lastLogTerm = raftState.lastLogTerm

  private def recentLogReport(limit: Int = 10): String = {
    recentLogs(limit)
      .map {
        case (entry, committed) => f"${entry.term.t}%3s | ${entry.index}%3s | ${if (committed) "committed" else " pending "}%10s | ${entry.command}"
      }
      .mkString("\n")
  }

  def recentLogs(limit: Int = 10) = {
    val to   = lastUnappliedIndex
    val from = (to - limit).max(0)
    logsEntriesBetween(from, to).toList.map { entry =>
      entry -> isCommitted(entry.index)
    }
  }

  def onVoteRequest(vote: RequestVote, cluster: ClusterProtocol): RequestVoteResponse = {

    val ourTerm = currentTerm
    val granted = {
      val termOk = ourTerm <= vote.term

      val indexOk = vote.lastLogIndex >= lastCommittedIndex && vote.lastLogTerm >= lastLogTerm

      val weHaventVotedForAnyoneElseThisTerm = {
        val voteOk = raftState.persistentState.votedFor.fold(true) { otherId =>
          otherId == vote.candidateId
        }
        voteOk || ourTerm < vote.term
      }

      logger.debug(s"""
        ${id} casting vote while in term ${ourTerm},
        lastCommittedIndex=${lastCommittedIndex} and
        lastLogTerm=${lastLogTerm}
        valuating vote request w/
        termOk ($termOk) &&
        indexOk ($indexOk) &&
        weHaventVotedForAnyoneElseThisTerm ($weHaventVotedForAnyoneElseThisTerm)
        w/ log \n${recentLogReport()}\n""")

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
    val (newState, reply) = raftState.append(id, ae)
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

  private def commitLogOnMajority(from: NodeId, ourLatestIndex: LogIndex, newView: Map[NodeId, ClusterPeer], cluster: ClusterProtocol) = {
    val nodesWithMatchIndex = newView.collect {
      case (nodeId, view) if view.matchIndex == ourLatestIndex => nodeId
    }

    val peersPlusUs = nodesWithMatchIndex.size + 1

    if (isMajority(peersPlusUs, cluster.clusterSize)) {
      val nodesToAck = if (ourLatestIndex == 0 || raftState.log.isCommitted(ourLatestIndex)) {
        // already committed - just ack on next heartbeat
        // (after all, this could be a response to an ack, so we'd just be ack-ing the ack!)
        Nil
      } else {
        // not yet committed - ack everybody
        raftState.log.commit(ourLatestIndex)
        nodesWithMatchIndex
      }

      logger.debug(s"Sending commit heartbeat to ${nodesToAck} for $ourLatestIndex")

      val commitRequest = mkHeartbeatAppendEntries(ourLatestIndex)
      nodesToAck.foreach(cluster.tell(_, commitRequest))
    }
  }

  /** We're either the leader or have been at some point, as we just got an append entries response
    *
    * @param from
    * @param resp
    * @param cluster
    */
  def onAppendEntriesResponse(from: NodeId, resp: AppendEntriesResponse, cluster: ClusterProtocol): Unit = {

    if (pendingAppendAcks.nonEmpty) {
      logger.info(s"Notifying ${pendingAppendAcks.size} of pending responses")
      val remaining = pendingAppendAcks.filterNot { clientResp =>
        val canRemove = clientResp.onResponse(from, resp)
        if (canRemove) {
          logger.debug(s"Removing pending ack ${clientResp}")
        }
        canRemove
      }
      pendingAppendAcks = remaining
    }

    for {
      view <- leaderState
      ourLatestLogIndex = lastUnappliedIndex
    } {
      val newNode = if (resp.success) {

        // cool - let's update our view!
        // ... and do we need to send any updates?
        val nextIndex = (resp.matchIndex + 1) //.min(ourLatestLogIndex)

        assert(resp.matchIndex <= ourLatestLogIndex, s"Match index '${resp.matchIndex}' from $from is >= our (leader's) largest index $ourLatestLogIndex")

        // do we need to send another append entries?
        if (resp.matchIndex < ourLatestLogIndex) {
          logEntryAt(nextIndex).foreach { entry =>
            require(nextIndex == nextIndex)
            cluster.tell(from, mkAppendEntries(entry.index, nextIndex, entry.term, entry.command))
          }
        }

        val newView = view.updated(from, ClusterPeer(resp.matchIndex, nextIndex))

        commitLogOnMajority(from, ourLatestLogIndex, newView, cluster)

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
            logger.debug(s"$id was granted vote from $from, becoming leader")
            val newRole = counter.leaderRole(id, lastCommittedIndex)
            raftState = raftState.copy(role = newRole)
            onLeaderTransition(cluster)
          case Some(false) =>
            logger.debug(s"$id was not granted vote from $from, we can't become leader")
            // we know now for definite that we won't become the leader.
            // hey ho ... let's just reset our timer and see how this all works out
            cluster.electionTimer.reset()
          case None =>
            logger.debug(s"$id was granted vote from $from, still waiting for majority")
          // nowt to do ... we don't have a definite result yet
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

  def logEntryAt(index: LogIndex): Option[LogEntry[T]] = raftState.log.at(index)

  def isCommitted(index: LogIndex): Boolean = raftState.log.isCommitted(index)

  def logsEntriesBetween(from: LogIndex, to: LogIndex = lastUnappliedIndex): immutable.IndexedSeq[LogEntry[T]] = {
    (from to to).flatMap(logEntryAt)
  }

  def mkAppendEntries(commitIndex: LogIndex, prevIdx: LogIndex, prevTerm: Term, command: T): AppendEntries[T] = {
    val ae = AppendEntries(
      term = currentTerm,
      leaderId = id,
      commitIndex = commitIndex,
      prevLogIndex = prevIdx,
      prevLogTerm = prevTerm,
      entry = Option(command)
    )

    ae
  }

  def mkHeartbeatAppendEntries(prevIdx: LogIndex = lastUnappliedIndex): AppendEntries[T] = {
    val ae = AppendEntries[T](
      term = currentTerm,
      leaderId = id,
      commitIndex = raftState.lastCommittedIndex,
      prevLogIndex = prevIdx,
      prevLogTerm = raftState.lastLogTerm,
      entry = None
    )

    ae
  }

  final def onResponse(from: NodeId, resp: RaftResponse, cluster: ClusterProtocol): Unit = resp match {
    case resp: RequestVoteResponse   => onRequestVoteResponse(from, resp, cluster)
    case resp: AppendEntriesResponse => onAppendEntriesResponse(from, resp, cluster)
  }

  override def toString = {
    val acks = pendingAppendAcks.mkString(s"${pendingAppendAcks.size} acks", "\n", "\n")
    s"""vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv Node $id vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
       |${pprint.apply(raftState)}
       |$acks
       |${recentLogReport()}
       |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Node $id ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
     """.stripMargin
  }

  override def equals(other: Any) = other match {
    case ss: RaftNodeLogic[T] => id == ss.id
    case _                    => false
  }

  override def hashCode = 17 * id.hashCode

}
