package riff.airc

import riff.raft.Peer

sealed trait LeaderAppend[A]

/**
  * The
  *
  * @param prevTerm the term of the log entry just prior
  * @param prevIndex
  */
case class LogState(prevTerm: Int, prevIndex: Int, commitIndex: Int)

case class AppendDataToLog[T](data: T) extends LeaderAppend[LogState]

case class AppendResponse(term: Int, matchIndex: Int, success: Boolean)

case class SendAppendRequest[T](to: String, logState: LogState, entries: Array[T]) extends LeaderAppend[AppendResponse]

object SendAppendRequest {
  def apply[T](to: Peer, logState: LogState, data: Array[T]): SendAppendRequest[T] = {
    val entries = if (to.matchIndex == logState.prevIndex) {
      data
    } else {
      Array.empty[T]
    }
    new SendAppendRequest[T](to.name, logState, entries)
  }
}

case object GetClusterNodes extends LeaderAppend[Set[Peer]]

case class CommitLog(logState: LogState) extends LeaderAppend[AppendResponse]

