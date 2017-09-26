package miniraft.state.rest

import io.circe._
import io.circe.syntax._
import miniraft.state._
import miniraft.state.rest.NodeStateSummary.NodeSnapshot

import scala.concurrent.{ExecutionContext, Future}

sealed trait NodeStateSummary {
  def summary: NodeSnapshot

  def withState(updatedState: Map[String, String]): NodeStateSummary
}

object NodeStateSummary {

  def apply(node: RaftNodeLogic[_], cluster: ClusterProtocol)(
      implicit ec: ExecutionContext): Future[NodeStateSummary] = {
    val role: NodeRole = node.raftState.role

    val electTimerStateF     = cluster.electionTimer.status
    val heartbeatTimerStateF = cluster.heartbeatTimer.status

    for {
      electionState       <- electTimerStateF
      heartbeatTimerState <- heartbeatTimerStateF

    } yield {

      val snapshot = {
        val ackPairs = node.pendingLeaderAcks.zipWithIndex.map {
          case (ack, i) => s"ack.$i" -> ack.toString
        }
        val stateMap = ackPairs.toMap.updated("pendingAckCount", node.pendingLeaderAcks.size.toString)

        NodeSnapshot(
          id = node.id,
          clusterNodes = cluster.clusterNodeIds,
          term = node.currentTerm,
          role = role.name,
          votedFor = node.leaderId,
          lastCommittedIndex = node.lastCommittedIndex,
          lastUnappliedIndex = node.lastUnappliedIndex,
          lastLogTerm = node.lastLogTerm,
          electionTimer = electionState,
          heartbeatTimer = heartbeatTimerState,
          stateMap
        )
      }

      role match {
        case Leader(view) => LeaderSnapshot(snapshot, view)
        case Candidate(counter) =>
          CandidateSnapshot(snapshot, counter.votedFor, counter.votedAgainst, counter.clusterSize)
        case Follower => snapshot
      }
    }
  }

  case class NodeSnapshot(id: NodeId,
                          clusterNodes: Set[NodeId],
                          term: Term,
                          role: String,
                          votedFor: Option[NodeId],
                          lastCommittedIndex: LogIndex,
                          lastUnappliedIndex: LogIndex,
                          lastLogTerm: Term,
                          electionTimer: String,
                          heartbeatTimer: String,
                          state: Map[String, String])
      extends NodeStateSummary {
    override def summary = this
    def isLeader         = role == "leader"

    override def withState(updatedState: Map[String, String]): NodeStateSummary = {
      summary.copy(state = updatedState)
    }
  }

  type FollowerSummary = NodeSnapshot

  case class LeaderSnapshot(override val summary: NodeSnapshot, clusterViewByNodeId: Map[NodeId, ClusterPeer])
      extends NodeStateSummary {
    override def withState(updatedState: Map[String, String]): NodeStateSummary = {
      val newSummary = summary.copy(state = updatedState)
      copy(summary = newSummary)
    }
  }

  case class CandidateSnapshot(override val summary: NodeSnapshot,
                               votedFor: Set[NodeId],
                               votedAgainst: Set[NodeId],
                               expectedVotes: Int)
      extends NodeStateSummary {
    override def withState(updatedState: Map[String, String]): NodeStateSummary = {
      val newSummary = summary.copy(state = updatedState)
      copy(summary = newSummary)
    }
  }

  implicit def NSSDecoder: Decoder[NodeStateSummary] = {

    import io.circe.generic.auto._
    val a: Decoder[NodeStateSummary] = implicitly[Decoder[NodeSnapshot]].map {
      case x: NodeStateSummary => x
    }
    val b: Decoder[NodeStateSummary] = implicitly[Decoder[LeaderSnapshot]].map {
      case x: NodeStateSummary => x
    }
    val c: Decoder[NodeStateSummary] = implicitly[Decoder[CandidateSnapshot]].map {
      case x: NodeStateSummary => x
    }
    a.or(b).or(c)
  }

  implicit object NSSEncoder extends Encoder[NodeStateSummary] {

    import io.circe.generic.auto._

    override def apply(a: NodeStateSummary): Json = a match {
      case nss: CandidateSnapshot => nss.asJson
      case nss: LeaderSnapshot    => nss.asJson
      case nss: NodeSnapshot      => nss.asJson
    }
  }

}
