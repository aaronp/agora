package riff

import riff.raft.{RaftMessage, RaftRequest}

/**
  * Sends a [[RaftRequest]] to another cluster node
  * @tparam F
  */
trait RaftClusterClient[F[_]] {

  /**
    * Sends the message to the cluster node and returns the reply
    * @param r
    * @return
    */
  def apply(r : RaftMessage) : F[RaftMessage]
}