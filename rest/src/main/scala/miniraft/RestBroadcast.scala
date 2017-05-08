package miniraft

import io.circe.{Decoder, Encoder}

import scala.concurrent.Future

class RestBroadcast(initialNodes: Map[NodeId, Transport]) extends Broadcast {
  private var nodesById = initialNodes

  override def nodeIds: Set[NodeId] = nodesById.keySet

  override def onRequestVote(req: RequestVote): Future[RequestVoteResponse] = {
    nodesById(req.to).onRequestVote(req)
  }

  override def onAppendEntries[T: Encoder : Decoder](req: AppendEntries[T]): Future[AppendEntriesResponse] = {
    nodesById(req.to).onAppendEntries(req)
  }
}

object RestBroadcast {
  def apply(initialNodes: Map[NodeId, Transport]) = new RestBroadcast(initialNodes)
}