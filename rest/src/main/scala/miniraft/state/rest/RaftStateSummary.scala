package miniraft.state.rest

import io.circe.syntax._
import io.circe._
import miniraft.state._
import miniraft.state.rest.NodeStateSummary.{LeaderSnapshot, NodeSnapshot}

import scala.concurrent.{ExecutionContext, Future}

sealed trait NodeStateSummary {
  def summary: NodeSnapshot
}

object NodeStateSummary {

  def apply(node: RaftNodeLogic[_], cluster: ClusterProtocol)(implicit ec: ExecutionContext): Future[NodeStateSummary] = {
    val role: NodeRole = node.raftState.role

    val electTimerStateF     = cluster.electionTimer.status
    val heartbeatTimerStateF = cluster.heartbeatTimer.status

    for {
      electionState       <- electTimerStateF
      heartbeatTimerState <- heartbeatTimerStateF

    } yield {

      val snapshot = NodeSnapshot(
        id = node.id,
        clusterSize = cluster.clusterSize,
        term = node.currentTerm,
        role = role.name,
        votedFor = node.leaderId,
        lastCommittedIndex = node.lastCommittedIndex,
        lastUnappliedIndex = node.lastUnappliedIndex,
        lastLogTerm = node.lastLogTerm,
        electionTimer = electionState,
        heartbeatTimer = heartbeatTimerState
      )

      role match {
        case Leader(view) =>
          LeaderSnapshot(snapshot, view)
        case Candidate(counter) =>
          CandidateSnapshot(snapshot, counter.votedFor, counter.votedAgainst, counter.clusterSize)
        case Follower =>
          snapshot
      }
    }
  }

  case class NodeSnapshot(id: NodeId,
                          clusterSize: Int,
                          term: Term,
                          role: String,
                          votedFor: Option[NodeId],
                          lastCommittedIndex: LogIndex,
                          lastUnappliedIndex: LogIndex,
                          lastLogTerm: Term,
                          electionTimer: String,
                          heartbeatTimer: String)
      extends NodeStateSummary {
    override def summary = this
  }

  type FollowerSummary = NodeSnapshot

  case class LeaderSnapshot(override val summary: NodeSnapshot, clusterViewByNodeId: Map[NodeId, ClusterPeer]) extends NodeStateSummary

  case class CandidateSnapshot(override val summary: NodeSnapshot, votedFor: Set[NodeId], votedAgainst: Set[NodeId], expectedVotes: Int) extends NodeStateSummary

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
