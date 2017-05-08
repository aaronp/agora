package miniraft

import io.circe.{Decoder, Encoder}

import scala.concurrent.Future

class Cluster(initialNodes: Set[RaftNode] = Set("A", "B", "C", "D", "E").map(RaftNode.apply)) {

  val nodesById: Map[NodeId, RaftNode] = {
    val map = initialNodes.map { node =>
      node.id -> node
    }.toMap
    map.values.foreach(_.setNodes(initialNodes.map(_.id)))
    map
  }

  def nodeIds = nodesById.keySet

  def size = nodesById.size

  def apply(id: NodeId) = nodesById(id)

  def triggerHeartbeatTimeout(id: NodeId, broadcast: Broadcast = EventBus, replyBroadcast: Broadcast = EventBus): Future[Set[RequestVoteResponse]] = {
    val responses = nodesById(id).onHeartbeatTimeout(broadcast)
    responses.foreach {
      case (_, future: Future[RequestVoteResponse]) =>
        future.onSuccess {
          case resp: RequestVoteResponse => nodesById(id).onRequestVoteResponse(resp, replyBroadcast)
        }
    }
    Future.sequence(responses.map(_._2))
  }

  object EventBus extends Broadcast {
    self =>
    override val nodeIds = nodesById.keySet


    override def onAppendEntries[T: Encoder : Decoder](req: AppendEntries[T]): Future[AppendEntriesResponse] = {
      Future.successful(nodesById(req.to).handleAppendEntries(req))
    }

    override def onRequestVote(req: RequestVote): Future[RequestVoteResponse] = {
      Future.successful(nodesById(req.to).handleRequestVote(req))
    }
  }

  def nodes = nodesById.values.toSet

  val client: ClusterClient = {
    new ClusterClient {
      def append[T: Encoder : Decoder](values: List[T]): Future[Boolean] = {
        nodes.find(_.isLeader) match {
          case Some(leader) => leader.clientAppend(values, EventBus)
          case None => Future.failed(new Exception("No leader available!"))
        }
      }
    }
  }
}