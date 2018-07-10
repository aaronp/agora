package riff

import riff.RaftState._
import sun.tools.java.ClassType


final case class RaftState[T: ClassType](node: RaftNode, actions: Iterable[ActionResult] = Nil, empty: T) {

  def update(action: RaftState.Action, now: Long): RaftState[T] = {
    action match {
      case MessageReceived(appendEntries: AppendEntries[T]) => onAppend(appendEntries, now)
      case MessageReceived(requestVote: RequestVote) => onRequestVote(requestVote, now)
      case MessageReceived(reply: RequestVoteReply) => onRequestVoteReply(reply, now)
      case MessageReceived(reply: AppendEntriesReply) => onAppendEntriesReply(reply, now)

      case ElectionTimeout =>
        node match {
          case follower: FollowerNode =>
            val candidate = follower.onElectionTimeout(now)
            copy(node = candidate, actions = RequestVote(candidate).map(SendMessage.apply))
          case _ =>
            copy(actions = LogMessage(s"Ignoring election timeout while ${node.name} is ${node.role}") :: Nil)
        }
    }
  }

  def onAppendEntriesReply(reply: AppendEntriesReply, now: Long): RaftState[T] = {
    node match {
      case leader : LeaderNode =>
        copy(leader.onAppendEntriesReply(reply), Nil)
      case n => copy(actions = List(LogMessage(s"Node '${n.name}' (${n.role}) ignoring $reply")))
    }
  }

  def onAppend[T](append: AppendEntries[T], now: Long): RaftState[T] = {
    val (newNode, reply) = node.onAppendEntries(append, now)
    copy(node = newNode, actions = SendMessage(reply) :: Nil)
  }

  def onRequestVote(requestVote: RequestVote, now: Long): RaftState[T] = {
    val (newNode, reply) = node.onRequestVote(requestVote, now)
    copy(node = newNode, actions = SendMessage(reply) :: Nil)
  }

  def onRequestVoteReply[T](requestVote: RequestVoteReply, now: Long): RaftState[T] = {
    node.onRequestVoteReply(requestVote, now) match {
      case leader: LeaderNode if node.isCandidate =>
        RaftState[T](leader, AppendEntries.forLeader(leader, empty).map(SendMessage), empty)
      case newNode => RaftState(newNode, Nil, empty)
    }
  }
}

object RaftState {

  sealed trait Action

  case object ElectionTimeout extends Action

  final case class MessageReceived(msg: RaftMessage) extends Action

  sealed trait ActionResult

  case class LogMessage(explanation: String, warn: Boolean = false) extends ActionResult

  final case class SendMessage(msg: RaftMessage) extends ActionResult

  final case class SendReply(reply: RaftReply) extends ActionResult

}
