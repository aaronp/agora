package riff

import riff.RaftState._

import scala.collection.immutable


final case class RaftState(node: RaftNode) {
  def update(action: RaftState.Action, now : Long): (RaftState, ActionResult) = {

    action match {
      case MessageReceived(msg) =>

      case ElectionTimeout =>
        node match {
          case follower : FollowerNode =>
            val candidate = follower.onElectionTimeout(now)
            copy(node = candidate) -> SendMessages(RequestVote(candidate))
          case _ =>
            this -> NoOp(s"Ignoring election timeout while ${node.name} is ${node.role}")
        }
    }

    ???

  }
}

object RaftState {

  sealed trait Action

  case object ElectionTimeout extends Action

  final case class MessageReceived(msg: RaftMessage) extends Action

  sealed trait ActionResult
  case class NoOp(explanation : String) extends ActionResult

  final case class SendMessages(msg: immutable.Iterable[RaftMessage]) extends ActionResult
  final case class SendReply(reply : RaftReply) extends ActionResult

}
