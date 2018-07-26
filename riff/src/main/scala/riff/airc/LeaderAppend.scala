package riff.airc

import java.nio.file.Path

import riff.raft.{LeaderOpinion, NodeRole, Peer}

sealed trait LeaderAppend[+T, A]

/**
  * The
  *
  * @param prevTerm the term of the log entry just prior
  * @param prevIndex
  */
case class LogState(prevTerm: Int, prevIndex: Int, commitIndex: Int)

case class AppendDataToLog[T](term : Int, data: T) extends LeaderAppend[T, LogState]


sealed trait AppendResponse

case class NodeResponse(from: String, term: Int, matchIndex: Int, success: Boolean) extends AppendResponse

case class NotTheLeader(leader: LeaderOpinion) extends AppendResponse

case object SingleNodeCluster extends AppendResponse

case class SendAppendRequest[T](to: String, logState: LogState, entries: Array[T]) extends LeaderAppend[T, AppendResponse]

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

case object GetClusterState extends LeaderAppend[Nothing, ClusterState]

case class ClusterState(peers: Set[Peer], leader: LeaderOpinion, role: NodeRole)

