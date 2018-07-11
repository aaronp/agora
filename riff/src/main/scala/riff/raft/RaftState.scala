package riff.raft

import riff.raft.RaftState._

import scala.reflect.ClassTag


/**
  * A container for the RaftNode which handles Raft Actions (timeouts, messages and replies)
  * @param node
  * @param actions
  * @param empty
  * @tparam T
  */
final case class RaftState[T: ClassTag](node: RaftNode, actions: Iterable[ActionResult] = Nil, empty: T) {

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
      case RemoveClusterNode(name) =>
        node.peersByName.get(name) {
          case Some(_) =>
            copy(node = node.withPeers(node.peersByName - name), LogMessage(s"${name} removed from the cluster") :: Nil, empty)
          case None =>
            copy(actions = LogMessage("$name is not in the cluster") :: Nil)
        }
      case AddClusterNode(peer) =>
        node.peersByName.get(peer.name) {
          case Some(_) =>
            copy(actions = LogMessage(s"${peer.name} is already in the cluster") :: Nil)
          case None =>
            copy(node = node.withPeers(node.peersByName.updated(peer.name, peer)), LogMessage(s"${peer.name} added") :: Nil, empty)
        }
    }
  }

  def onAppendEntriesReply(reply: AppendEntriesReply, now: Long): RaftState[T] = {
    node match {
      case leader: LeaderNode =>
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

  def apply[T](name: String, empty: T): RaftState[T] = RaftState(RaftNode(name), Nil, empty)

  sealed trait Action

  case object ElectionTimeout extends Action

  final case class MessageReceived(msg: RaftMessage) extends Action

  final case class AddClusterNode(node: Peer) extends Action

  final case class RemoveClusterNode(node: String) extends Action

  sealed trait ActionResult

  case class LogMessage(explanation: String, warn: Boolean = false) extends ActionResult

  final case class SendMessage(msg: RaftMessage) extends ActionResult

  final case class SendReply(reply: RaftReply) extends ActionResult

}
