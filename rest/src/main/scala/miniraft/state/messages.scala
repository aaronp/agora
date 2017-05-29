package miniraft.state


sealed trait RaftRequest

sealed trait RaftResponse

/**
  * TODO - add our latest commit (applied) index
  *
  * @param term
  * @param candidateId
  * @param lastLogIndex our latest log index
  * @param lastLogTerm
  */
case class RequestVote(term: Term, candidateId: NodeId, lastLogIndex: LogIndex, lastLogTerm: Term) extends RaftRequest

case class RequestVoteResponse(term: Term, granted: Boolean) extends RaftResponse

/**
  *
  * @param term         our (the leader's) term
  * @param leaderId     our (the leader's) id
  * @param prevLogIndex the last known (or assumed) index as we know it. If the reciever replies w/ a non-success response, this index is decremented and retried
  * @param prevLogTerm  our previous log term
  * @param entries      the entries to append ... which are only non-empty if a previous AppendEntries request came back successfully
  * @param leaderCommit our latest commit index
  * @tparam T
  */
case class AppendEntries[T](term: Term, leaderId: NodeId, prevLogIndex: LogIndex, prevLogTerm: Term, entries: List[LogEntry[T]], leaderCommit: LogIndex) extends RaftRequest {
  require(leaderId.nonEmpty)
}

case class AppendEntriesResponse(term: Term, success: Boolean, matchIndex: LogIndex) extends RaftResponse