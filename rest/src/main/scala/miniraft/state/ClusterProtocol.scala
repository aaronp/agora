package miniraft.state

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}
import miniraft._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Represents a view of the cluster to a RaftNode.
  * This is passed in, allowing the RaftNode (whos responsibility is to maintain its own state)
  * to communicate with the rest of the cluster and control its timeouts
  */
trait ClusterProtocol {
  def tell(id: NodeId, raftRequest: RaftRequest): Unit

  def tellOthers(raftRequest: RaftRequest): Unit

  def clusterNodeIds: Set[NodeId]

  def clusterSize: Int = clusterNodeIds.size

  /** @return the timer to use when in follower state
    */
  def electionTimer: RaftTimer

  /** @return the timer to use while in leader state
    */
  def heartbeatTimer: RaftTimer
}

object ClusterProtocol {

  /**
    * An implementation which allows you to replace the underlying implementation
    */
  class DelegateProtocol(underlying: ClusterProtocol) extends ClusterProtocol {
    private var cluster = underlying

    def update(c: ClusterProtocol) = cluster = c

    override def tell(id: NodeId, raftRequest: RaftRequest) = cluster.tell(id, raftRequest)

    override def tellOthers(raftRequest: RaftRequest) = cluster.tellOthers(raftRequest)

    override def clusterNodeIds = cluster.clusterNodeIds

    override def electionTimer = cluster.electionTimer

    override def heartbeatTimer = cluster.heartbeatTimer

    override def toString = s"Delegate to: $cluster"
  }

  class LoggingProtocol[T: Encoder: Decoder](p: ClusterProtocol, saveUnder: Path, counter: AtomicInteger, numberOfMessageToKeep: Int) extends DelegateProtocol(p) {

    import io.circe.generic.auto._
    import io.circe.syntax._
    import agora.domain.io.implicits._

    private def save(suffix: String, req: RaftRequest) = {
      val msgIdx = counter.incrementAndGet()

      val content = req match {
        case ae: AppendEntries[T] if ae.isHeartbeat => ae.asJson.spaces2
        case ae: AppendEntries[T]                   => ae.asJson.spaces2
        case vote: RequestVote                      => vote.asJson.spaces2
      }

      saveUnder.resolve(s"${msgIdx}-$suffix").text = content
      //      val threshold = msgIdx - numberOfMessageToKeep
      //      LoggingEndpoint.removeFilesWithAnIntegerPrefixPriorToN(saveUnder, threshold)
    }

    override def tell(id: NodeId, raftRequest: RaftRequest) = {
      save(s"tell-$id", raftRequest)
      super.tell(id, raftRequest)
    }

    override def tellOthers(raftRequest: RaftRequest) = {
      save("tell-others", raftRequest)
      super.tellOthers(raftRequest)
    }

  }

  class Buffer[T](ourNodeId: NodeId, election: RaftTimer, heartbeat: RaftTimer, initialNodes: Map[NodeId, _ <: RaftEndpoint[T]] = Map.empty)(
      implicit override val executionContext: ExecutionContext)
      extends BaseProtocol[T](ourNodeId, initialNodes, election, heartbeat) {

    type Handler = (NodeId, RaftEndpoint[T], RaftRequest, RaftResponse) => Unit
    private var clusterNodes             = initialNodes
    private var handler: Option[Handler] = None

    def update(nodes: Map[NodeId, _ <: RaftEndpoint[T]]) = {
      clusterNodes = nodes
    }

    override def clusterNodesById: Map[NodeId, _ <: RaftEndpoint[T]] = clusterNodes

    def updateHander(onResponse: Handler) = {
      handler = Option(onResponse)
    }

    override def onResponse(from: NodeId, endpoint: RaftEndpoint[T], raftRequest: RaftRequest, response: RaftResponse): Unit = {
      handler.foreach { f =>
        f(from, endpoint, raftRequest, response)
      }
    }
  }

  /** A basic implementation of protocol based on a map of node entpoints
    */
  abstract class BaseProtocol[T](val ourNodeId: NodeId,
                                 val initialNodes: Map[NodeId, _ <: RaftEndpoint[T]],
                                 override val electionTimer: RaftTimer,
                                 override val heartbeatTimer: RaftTimer)
      extends ClusterProtocol
      with StrictLogging {
    implicit protected def executionContext: ExecutionContext

    protected def otherNodes: Map[NodeId, RaftEndpoint[T]] = clusterNodesById - ourNodeId

    /** Required by subclasses to provide a means to do something w/ a reply
      *
      * @param from        the node an initial request was sent to
      * @param endpoint    the endpoint who received our RaftRequest and has now replied
      * @param raftRequest the request sent to the endpoint
      * @param response    the response received
      */
    def onResponse(from: NodeId, endpoint: RaftEndpoint[T], raftRequest: RaftRequest, response: RaftResponse): Unit

    def clusterNodesById = initialNodes

    override def tell(id: NodeId, raftRequest: RaftRequest): Unit = {
      clusterNodesById.get(id) match {
        case None =>
          logger.error(s"Attempt to send to unknown node $id")
        case Some(endpoint) =>
          val future: Future[RaftResponse] = endpoint.onRequest(raftRequest)
          future.onSuccess {
            case response =>
              onResponse(id, endpoint, raftRequest, response)
          }
      }
    }

    override def tellOthers(raftRequest: RaftRequest): Unit = {
      otherNodes.keySet.foreach(tell(_, raftRequest))
    }

    override def clusterNodeIds: Set[NodeId] = clusterNodesById.keySet
  }

}
