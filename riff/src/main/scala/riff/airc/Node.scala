package riff.airc

import cats.free.Free
import riff.raft.IsEmpty

sealed trait Node[A]

final case class AppendEntry[T : IsEmpty](from: String, to: String, term: Int, prevIndex: Int, prevTerm: Int, entries: T, commitIndex: Int) extends Node[AppendEntriesReply]
final case class AppendEntriesReply(from: String, to: String, term: Int, success: Boolean, matchIndex: Int) extends Node[LogCoords]

final case class RequestVote(from: String, to: String, term: Int, lastLogIndex: Int, lastLogTerm: Int) extends Node[RequestVoteReply]
final case class RequestVoteReply(from: String, to: String, term: Int, granted: Boolean) extends Node[VoteDecision]


final case class VoteDecision(grantedCount : Int, rejectedCount : Int, clusterSize : Int)

object Node {

//  def appendEntry[T : IsEmpty](from: String, to: String, term: Int, prevIndex: Int, prevTerm: Int, entries: T, commitIndex: Int): Free[Node, AppendEntriesReply] = {
//    Free.liftF(AppendEntry[T](from, to, term, prevIndex, prevTerm, entries, commitIndex))
//  }
//  def appendEntryReply(from: String, to: String, term: Int, success: Boolean, matchIndex: Int): Free[Node, LogCoords] = {
//    Free.liftF(AppendEntriesReply(from, to, term, success, matchIndex))
//  }
//
//  def followerAppend[T](append : AppendEntry[T]) = {
//    for {
//      reply <- Free.liftF(append)
//    } yield {
//      reply
//    }
//  }
}
