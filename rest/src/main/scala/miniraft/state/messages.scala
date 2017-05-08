package miniraft.state


sealed trait RaftRequest

sealed trait RaftResponse

case class RequestVote(term: Term, candidateId: NodeId, lastLogIndex: LogIndex, lastLogTerm: Term) extends RaftRequest

case class RequestVoteResponse(term: Term, granted: Boolean) extends RaftResponse

case class AppendEntries(term: Term, leaderId: NodeId, prevLogIndex: LogIndex, prevLogTerm: Term, entries: List[LogEntry], leaderCommit: LogIndex) extends RaftRequest

case class AppendEntriesResponse(term: Term, success: Boolean, matchIndex: LogIndex) extends RaftResponse