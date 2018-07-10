package riff

import scala.collection.immutable

sealed trait RaftMessage

object RaftMessage {

  def requestVote(node: CandidateNode): immutable.Iterable[RequestVote] = {
    node.peersByName.map {
      case (to, peer) =>
        require(to == peer.name)
        RequestVote.createForPeer(node.name, node.currentTerm, peer)
    }
  }
}

/**
  * When an AppendEntries request is received by a follower,
  *
  * @param from
  * @param to
  * @param term the term of the leader node sending the request, and the commit index
  * @param prevIndex the index of the last _uncommitted_ append
  * @param prevTerm the term of the last commit
  * @param entries the data to append, which may be blank when a new leader is first elected
  * @param commitIndex the index of the latest committed append
  * @tparam T
  */
case class AppendEntries[T](from: String, to: String, term: Int, prevIndex: Int, prevTerm: Int, entries: T, commitIndex: Int) extends RaftMessage

object AppendEntries {

  /**
    * @param leader
    * @param empty
    * @tparam T
    * @return an empty appendEntries request to signal new cluster leadership
    */
  def forLeader[T](leader: LeaderNode, empty: T): immutable.Iterable[AppendEntries[T]] = {

    leader.peersByName.map {
      case (_, peer) =>
        new AppendEntries[T](
          from = leader.name,
          to = peer.name,
          term = leader.currentTerm,
          prevIndex = leader.commitIndex - 1,
          prevTerm = leader.currentTerm - 1,
          entries = empty,
          commitIndex = peer.matchIndex)
    }

  }
}

case class RequestVote(from: String, to: String, term: Int, lastLogIndex: Int, lastLogTerm: Int) extends RaftMessage

object RequestVote {
  def createForPeer(from: String, currentTerm: Int, peer: Peer) = {
    RequestVote(from, peer.name,
      term = currentTerm,
      lastLogIndex = peer.matchIndex,
      lastLogTerm = peer.matchIndex)
  }
}


sealed trait RaftReply

case class RequestVoteReply(from: String, to: String, sent: Long, term: Int, granted: Boolean) extends RaftReply

case class AppendEntriesReply(from: String, to: String, sent: Long, term: Int, success: Boolean, matchIndex: Int) extends RaftReply

