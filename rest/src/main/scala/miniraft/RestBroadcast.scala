package miniraft

import io.circe.{Decoder, Encoder}

import scala.concurrent.Future

class RestBroadcast(initialNodes : Map[NodeId, Transport]) extends Broadcast {
  override def nodeIds: Set[NodeId] = initialNodes.keySet

  var nodesById = initialNodes
  def nodes = nodesById.values
  override def onRequestVote(req: RequestVote): Future[RequestVoteResponse] = {
    nodesById(req.to).onRequestVote(req)
  }

  override def onAppendEntries[T : Encoder : Decoder](req: AppendEntries[T]): Future[AppendEntriesResponse] = {
    nodesById(req.to).onAppendEntries(req)
  }
}
