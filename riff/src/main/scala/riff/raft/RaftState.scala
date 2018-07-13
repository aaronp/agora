package riff.raft

import cats.free.Free
import riff.raft.RaftState._

import scala.reflect.ClassTag


/**
  * A container for the RaftNode which handles Raft Actions (timeouts, messages and replies)
  *
  * @param node
  * @param actions
  * @tparam T
  */
final case class RaftState[T: IsEmpty : ClassTag](node: RaftNode, logState: CommitLogState, actions: Iterable[ActionResult] = Nil) {

  private val empty = IsEmpty[T].empty

  def asLeader: Option[LeaderNode] = Option(node).collect {
    case leader: LeaderNode => leader
  }

  def update(action: RaftState.Action[_], now: Long): RaftState[T] = {
    action match {
      case MessageReceived(appendEntries: AppendEntries[T]) => onAppend(appendEntries, now)
      case MessageReceived(requestVote: RequestVote) => onRequestVote(requestVote, now)
      case MessageReceived(reply: RequestVoteReply) => onRequestVoteReply(reply, now)
      case MessageReceived(reply: AppendEntriesReply) => onAppendEntriesReply(reply, now)
      case OnElectionTimeout =>
        node match {
          case follower: FollowerNode =>
            val candidate = follower.onElectionTimeout(now)
            copy(node = candidate, actions = RequestVote(candidate).map(SendMessage.apply))
          case _ =>
            copy(actions = LogMessage(s"Ignoring election timeout while ${node.name} is ${node.role}") :: Nil)
        }
      case RemoveClusterNode(name) =>
        node.peersByName.get(name) match {
          case Some(_) =>
            copy(node = node.withPeers(node.peersByName - name), actions = LogMessage(s"${name} removed from the cluster") :: Nil)
          case None =>
            copy(actions = LogMessage("$name is not in the cluster") :: Nil)
        }
      case AddClusterNode(peer) =>
        node.peersByName.get(peer.name) match {
          case Some(_) =>
            copy(actions = LogMessage(s"${peer.name} is already in the cluster") :: Nil)
          case None =>
            copy(node = node.withPeers(node.peersByName.updated(peer.name, peer)), actions = LogMessage(s"${peer.name} added") :: Nil)
        }
    }
  }

  private def onAppendEntriesReply(reply: AppendEntriesReply, now: Long): RaftState[T] = {
    node match {
      case leader: LeaderNode =>
        copy(leader.onAppendEntriesReply(reply), actions = Nil)
      case n => copy(actions = List(LogMessage(s"Node '${n.name}' (${n.role}) ignoring $reply")))
    }
  }

  private def onAppend(append: AppendEntries[T], now: Long): RaftState[T] = {
    val (newNode, reply) = node.onAppendEntries(append, now)
    import IsEmpty.ops._
    val msgs = if (reply.success && append.entries.nonEmpty) {
      AppendLogEntry(reply.term, reply.matchIndex - 1, append.entries) :: SendMessage(reply) :: Nil
    } else {
      SendMessage(reply) :: Nil
    }
    copy(node = newNode, actions = msgs)
  }

  private def onRequestVote(requestVote: RequestVote, now: Long): RaftState[T] = {
    val (newNode, reply) = node.onRequestVote(requestVote, now)
    copy(node = newNode, actions = SendMessage(reply) :: Nil)
  }

  private def onRequestVoteReply(requestVote: RequestVoteReply, now: Long): RaftState[T] = {
    node.onRequestVoteReply(requestVote, logState, now) match {
      case leader: LeaderNode if node.isCandidate =>
        copy(node = leader, actions = AppendEntries.forLeader(leader, empty).map(SendMessage))
      case newNode => copy(newNode, actions = Nil)
    }
  }
}

object RaftState {

  def of[T: IsEmpty : ClassTag](name: String, logState: CommitLogState = CommitLogState.Empty): RaftState[T] = RaftState(RaftNode(name), logState, Nil)

  sealed trait Action[A]

  object Action {
    def onElectionTimeout: Free[Action, RequestVote] = Free.liftF(OnElectionTimeout)

    def onMsg(msg: RaftMessage): Free[Action, List[RaftMessage]] = Free.liftF(MessageReceived(msg))

    def addNode(name: String): Free[Action, Boolean] = Free.liftF(AddClusterNode(Peer(name)))

    def removeNode(name: String): Free[Action, Boolean] = Free.liftF(RemoveClusterNode(name))

    /**
      * Append some data.
      *
      * If the state is the leader node, then it will create 1 or more [[AppendLogEntry]] messages (to ourselves and the cluster members)
      *
      * @param state
      * @param data
      * @tparam T
      * @return
      */
    def append[T](state: RaftState[T], data: T) = {
      state.asLeader.map(_.makeAppend(data))
    }
  }

  case object OnElectionTimeout extends Action[RequestVote]

  final case class MessageReceived(msg: RaftMessage) extends Action[List[RaftMessage]]

  final case class AddClusterNode(node: Peer) extends Action[Boolean]

  final case class RemoveClusterNode(node: String) extends Action[Boolean]

  sealed trait ActionResult

  case class LogMessage(explanation: String, warn: Boolean = false) extends ActionResult

  final case class SendMessage(msg: RaftMessage) extends ActionResult

  final case class AppendLogEntry[T](term: Int, index: Int, data: T) extends ActionResult

  final case class CommitLogEntry(term: Int, index: Int) extends ActionResult

  final case object ResetSendHeartbeatTimeout extends ActionResult

  final case object ResetElectionTimeout extends ActionResult

}
