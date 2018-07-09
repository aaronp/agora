package riff

import scala.collection.immutable

sealed trait RaftMessage

object RaftMessage {

  def requestVote(node :CandidateNode): immutable.Iterable[RequestVote] = {
    node.peersByName.map {
      case (to, peer) =>
        require(to == peer.name)
        RequestVote(node.name, to,
          term = node.currentTerm,
          lastLogIndex = peer.matchIndex,
          lastLogTerm = peer.matchIndex)
    }
  }
}

case class AppendEntries[T](from: String, to: String, term: Int, prevIndex: Int, prevTerm: Int, entries: T, commitIndex: Int) extends RaftMessage

case class RequestVote(from: String, to: String, term: Int, lastLogIndex: Int, lastLogTerm: Int) extends RaftMessage


sealed trait RaftReply
case class RequestVoteReply(from : String, to : String, sent : Long, term : Int, granted : Boolean) extends RaftReply
case class AppendEntriesReply(from : String, to : String, sent : Long, term : Int, success: Boolean, matchIndex: Int) extends RaftReply

