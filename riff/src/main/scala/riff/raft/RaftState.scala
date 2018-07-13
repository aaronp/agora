package riff.raft

import cats.free.Free
import riff.raft.RaftState._


/**
  * A container for the RaftNode which handles Raft Actions (timeouts, messages and replies)
  *
  * @param node
  * @tparam T
  */
final case class RaftState[T: IsEmpty](node: RaftNode, logState: CommitLogState) {

  private val empty = IsEmpty[T].empty

  def asLeader: Option[LeaderNode] = Option(node).collect {
    case leader: LeaderNode => leader
  }


  def addNode(peer : Peer): Option[RaftState[T]] = {
    node.peersByName.get(peer.name) match {
      case Some(_) => None
      case None => Option(copy(node = node.withPeers(node.peersByName.updated(peer.name, peer))))
    }
  }

  def removeNode(name : String): Option[RaftState[T]] = {
    node.peersByName.get(name).map(_ => copy(node = node.withPeers(node.peersByName - name)))
  }

  private def onAppend(append: AppendEntries[T]): (RaftState[T], List[ActionResult]) = {
    val (newNode, reply) = node.onAppendEntries(append)
    import IsEmpty.ops._
    val msgs = if (reply.success && append.entries.nonEmpty) {
      AppendLogEntry(LogCoords(reply.term, reply.matchIndex - 1), append.entries) :: SendMessage(reply) :: Nil
    } else {
      SendMessage(reply) :: Nil
    }
    copy(node = newNode) -> msgs
  }

  def onMessage(msg : RaftMessage): (RaftState[T], Iterable[ActionResult]) = update(MessageReceived(msg))

  def update(action: RaftState.Action[_]): (RaftState[T], Iterable[ActionResult]) = {
    action match {
      case MessageReceived(appendEntries: AppendEntries[T]) => onAppend(appendEntries)
      case MessageReceived(requestVote: RequestVote) => onRequestVote(requestVote)
      case MessageReceived(reply: RequestVoteReply) => onRequestVoteReply(reply)
      case MessageReceived(reply: AppendEntriesReply) => onAppendEntriesReply(reply)
      case OnElectionTimeout =>
        node match {
          case follower: FollowerNode =>
            val candidate = follower.onElectionTimeout()
            val actions = RequestVote(candidate).map(SendMessage.apply)
            copy(node = candidate) -> actions
          case _ =>
            val actions = LogMessage(s"Ignoring election timeout while ${node.name} is ${node.role}") :: Nil
            this -> actions
        }
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

  def of[T: IsEmpty](name: String, logState: CommitLogState = CommitLogState.Empty): RaftState[T] = RaftState(RaftNode(name), logState)

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
    def append[T](state: RaftState[T], data: T): Option[Seq[AppendEntries[T]]] = {
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
  final case class AppendLogEntry[T](coords : LogCoords, data: T) extends ActionResult
  final case class CommitLogEntry(coords : LogCoords) extends ActionResult
  final case object ResetSendHeartbeatTimeout extends ActionResult
  final case object ResetElectionTimeout extends ActionResult

}
