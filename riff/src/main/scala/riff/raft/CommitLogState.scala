package riff.raft

final case class CommitLogState(prevLogInex : Int, prevLogTerm : Int, commitIndex :Int)

object CommitLogState {
  val Empty = CommitLogState(0,0,0)
}
