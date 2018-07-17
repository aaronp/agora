package riff.raft

import riff.raft.RaftState._

import scala.reflect.ClassTag


/**
  * A container for the RaftNode which handles Raft Actions (timeouts, messages and replies)
  *
  * @param node
  * @tparam T
  */
final case class RaftState[T: ClassTag : IsEmpty](node: RaftNode, logState: CommitLogState) {

  private val isEmptyInst = IsEmpty.instance[T]
  private val empty = isEmptyInst.empty

  def asLeader: Option[LeaderNode] = Option(node).collect {
    case leader: LeaderNode => leader
  }


  def addNode(peer: Peer): Option[RaftState[T]] = {
    node.peersByName.get(peer.name) match {
      case Some(_) => None
      case None => Option(copy(node = node.withPeers(node.peersByName.updated(peer.name, peer))))
    }
  }

  def removeNode(name: String): Option[RaftState[T]] = {
    node.peersByName.get(name).map(_ => copy(node = node.withPeers(node.peersByName - name)))
  }

  private def onAppend(append: AppendEntries[T]): (RaftState[T], List[ActionResult]) = {
    val (newNode, reply) = node.onAppendEntries(append)

    val msgs = if (reply.success && isEmptyInst.nonEmpty(append.entries)) {
      AppendLogEntry(LogCoords(reply.term, reply.matchIndex - 1), append.entries) :: SendMessage(reply) :: Nil
    } else {
      SendMessage(reply) :: Nil
    }
    copy(node = newNode) -> msgs
  }

  def onMessage(msg: RaftMessage, now: Long): (RaftState[T], Iterable[ActionResult]) = update(MessageReceived(msg), now)

  def onSendHeartbeatTimeout(now: Long): (RaftState[T], Iterable[ActionResult]) = {
    node match {
      case leader: LeaderNode =>
        val newLeader = leader.onSendHeartbeatTimeout(now)
        //val actions = RequestVote(candidate).map(SendMessage.apply)
        //        copy(node = candidate) -> actions
        ???
      case _ =>
        val actions = LogMessage(s"Ignoring send heartbeat timeout while ${node.name} is ${node.role}") :: Nil
        this -> actions
    }
  }

  def onReceiveHeartbeatTimeout(): (RaftState[T], Iterable[ActionResult]) = {
    node match {
      case follower: FollowerNode =>
        val candidate = follower.onElectionTimeout()
        val actions = ResetElectionTimeout +: RequestVote(candidate).map(SendMessage.apply)
        copy(node = candidate) -> actions
      case _ =>
        val actions = LogMessage(s"Ignoring received heartbeat (election timeout) while ${node.name} is ${node.role}") :: Nil
        this -> actions
    }
  }

  def update(action: RaftState.Action[_], now: Long): (RaftState[T], Iterable[ActionResult]) = {
    action match {
      case MessageReceived(appendEntries: AppendEntries[T]) => onAppend(appendEntries)
      case MessageReceived(requestVote: RequestVote) => onRequestVote(requestVote)
      case MessageReceived(reply: RequestVoteReply) => onRequestVoteReply(reply)
      case MessageReceived(reply: AppendEntriesReply) => onAppendEntriesReply(reply)
      case OnSendHeartbeatTimeout => onSendHeartbeatTimeout(now)
      case OnReceiveHeartbeatTimeout => onReceiveHeartbeatTimeout()
      case RemoveClusterNode(name) =>
        removeNode(name) match {
          case None =>
            this -> List(LogMessage(s"$name is not in the cluster"))
          case Some(newState) =>
            newState -> List(LogMessage(s"$name removed from the cluster"))
        }
      case AddClusterNode(peer) =>
        addNode(peer) match {
          case Some(newState) => newState -> List(LogMessage(s"${peer.name} was added to the cluster"))
          case None => this -> List(LogMessage(s"${peer.name} is already in the cluster"))
        }

    }
  }

  private def onAppendEntriesReply(reply: AppendEntriesReply): (RaftState[T], List[ActionResult]) = {
    node match {
      case leader: LeaderNode =>
        copy(leader.onAppendEntriesReply(reply)) -> Nil
      case n => this -> List(LogMessage(s"Node '${n.name}' (${n.role}) ignoring $reply"))
    }
  }


  private def onRequestVote(requestVote: RequestVote): (RaftState[T], List[ActionResult]) = {
    val (newNode, reply) = node.onRequestVote(requestVote)
    copy(node = newNode) -> List(SendMessage(reply))
  }

  private def onRequestVoteReply(requestVote: RequestVoteReply): (RaftState[T], Iterable[ActionResult]) = {
    node.onRequestVoteReply(requestVote, logState) match {
      case leader: LeaderNode if node.isCandidate =>
        copy(node = leader) -> AppendEntries.forLeader(leader, empty).map(SendMessage)
      case newNode => copy(newNode) -> Nil
    }
  }
}

object RaftState {

  def of[T: ClassTag : IsEmpty](name: String, logState: CommitLogState = CommitLogState.Empty): RaftState[T] = RaftState[T](RaftNode(name), logState)

  sealed trait Action[A]

  case object OnReceiveHeartbeatTimeout extends Action[RequestVote]

  case object OnSendHeartbeatTimeout extends Action[RequestVote]

  final case class MessageReceived(msg: RaftMessage) extends Action[List[RaftMessage]]

  final case class AddClusterNode(node: Peer) extends Action[Boolean]

  final case class RemoveClusterNode(node: String) extends Action[Boolean]

  sealed trait ActionResult

  case class LogMessage(explanation: String, warn: Boolean = false) extends ActionResult

  final case class SendMessage(msg: RaftMessage) extends ActionResult

  final case class AppendLogEntry[T](coords: LogCoords, data: T) extends ActionResult

  final case class CommitLogEntry(coords: LogCoords) extends ActionResult

  final case object ResetSendHeartbeatTimeout extends ActionResult

  final case object ResetElectionTimeout extends ActionResult

}
