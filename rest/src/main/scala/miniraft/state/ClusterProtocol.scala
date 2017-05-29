package miniraft.state

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


/**
  * Represents a view of the clusterto a RaftNode.
  * This is passed in, allowing the RaftNode (whos responsibility is to maintain its own state)
  * to communicate with the rest of the cluster and control its timeouts
  */
trait ClusterProtocol {
  def tell(id: NodeId, raftRequest: RaftRequest): Unit

  def tellOthers(raftRequest: RaftRequest): Unit

  def clusterNodeIds: Set[NodeId]

  def clusterSize: Int = clusterNodeIds.size

  def electionTimer: RaftTimer

  def heartbeatTimer: RaftTimer
}

object ClusterProtocol {
  def apply[T](raftNode: RaftNode[T],
               initialCluster: Map[NodeId, RaftEndpoint[T]],
               electionTimer: RaftTimer,
               heartbeatTimer: RaftTimer)(implicit ec: ExecutionContext) = {
    new Default(initialCluster, raftNode, electionTimer, heartbeatTimer)
  }


  /**
    * Represents a single raft node's view of the cluster.
    * It is also responsible for providing the transport to the RaftNode
    * so that can do its stuff
    */
  class Default[T](initialCluster: Map[NodeId, RaftEndpoint[T]],
                   raftNode: RaftNode[T],
                   override val electionTimer: RaftTimer,
                   override val heartbeatTimer: RaftTimer)(implicit ec: ExecutionContext)
    extends ClusterProtocol
      with LazyLogging {
    self =>

    // TODO - we could make this mutable and allow cluster resizing
    private val cluster = initialCluster

    override def tell(id: NodeId, raftRequest: RaftRequest): Unit = {
      cluster(id).onRequest(raftRequest).onComplete {
        case Success(resp) =>
          raftNode.onResponse(id, resp, self)
        case Failure(err) => logger.error(s"Telling $id ${pprint.stringify(raftRequest)} resulted in $err")
      }
    }

    override def tellOthers(raftRequest: RaftRequest): Unit = {
      clusterNodeIds.filterNot(_ == raftNode.id).foreach(id => tell(id, raftRequest))
    }

    override def clusterNodeIds: Set[NodeId] = cluster.keySet

    lazy val endpoint: RaftEndpoint[T] = raftNode.endpoint(this)

    def electionTimeout = raftNode.onElectionTimeout(self)

    def leaderId = raftNode.leaderId

  }

}