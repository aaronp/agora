package riff

object Raft {

  sealed trait RaftAction

  case object ElectionTimeout extends RaftAction
  case class MessageReceived(msg : RaftMessage) extends RaftAction

  //case object ElectionTimeout extends RaftAction

  /**
    * Representation of the raft cluster v
    * @param raftNodesByName
    * @tparam F
    */
  case class RaftClusterView[F[_]](raftNodesByName : Map[String, RaftNode]) {
    def update(action : RaftAction) = {
      ???
    }
  }
}
