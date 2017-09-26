package miniraft

import miniraft.state._

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
  * The first request sent should be
  * commitIndex: 0
  * prevIndex  : 0
  * prevTerm   : 0
  *
  * (to which the initial response would be)
  * matchIndex: 1 // the most recent uncommitted index
  *
  * The next request would be:
  *
  * commmitIndex : 1
  * prevIndex : 1 // the recipient's match index
  * prevTerm : 2 // the first term which has a leader
  *
  *
  *
  * @param term         our (the leader's) term
  * @param leaderId     our (the leader's) id
  * @param commitIndex  the index this commit is for.
  * @param prevLogIndex the last known (or assumed) index as we know it. If the receiver replies w/ a non-success response, this index is decremented and retried
  * @param prevLogTerm  the term of the log index
  * @param entry        the entry to append ... which are only non-empty if a previous AppendEntries request came back successfully
  * @tparam T
  */
case class AppendEntries[T](term: Term,
                            leaderId: NodeId,
                            commitIndex: LogIndex,
                            prevLogIndex: LogIndex,
                            prevLogTerm: Term,
                            entry: Option[T])
    extends RaftRequest {
  require(leaderId.nonEmpty)
  def isHeartbeat = entry.isEmpty
}

case class AppendEntriesResponse(term: Term, success: Boolean, matchIndex: LogIndex) extends RaftResponse
