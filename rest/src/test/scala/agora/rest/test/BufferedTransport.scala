package agora.rest.test

import miniraft.RaftRequest
import miniraft.state._

class BufferedTransport(from: NodeId, clusterId: Set[NodeId]) extends ClusterProtocol {

  private var sentResponses: List[SentResponse] = Nil

  def replies = sentResponses
  def flushReplies() = {
    val all = sentResponses
    sentResponses = Nil
    all
  }

  private var sentRequests = List[SentRequest]()

  def pendingRequests = sentRequests

  override val electionTimer = new TestTimer()

  override val heartbeatTimer = new TestTimer()

  override def toString = {
    s"""$from has ${sentRequests.size} pending requests to send:
       |${sentRequests.mkString("\n")}
       |
       |and ${sentResponses.size} pending responses yet to be applied
       |${sentResponses.mkString("\n")}
     """.stripMargin
  }

  def +=(response: SentResponse) = {
    require(response.sent.from == from)
    sentResponses = response :: sentResponses
  }

  private var clusterView = Set[NodeId]()

  def updateClusterView(clusterIds: Set[NodeId]) = {
    clusterView = clusterIds
    this
  }

  override def clusterNodeIds: Set[NodeId] = clusterView

  def removeRequest(r: SentRequest) = {
    require(sentRequests.contains(r))
    sentRequests = sentRequests diff List(r)
    require(!sentRequests.contains(r))
    this
  }

  override def tell(id: NodeId, raftRequest: RaftRequest) = {
    require(id != from, "nodes shouldn't send messages to themselves")
    sentRequests = sentRequests :+ SentRequest(from, id, raftRequest)
  }

  override def tellOthers(raftRequest: RaftRequest): Unit = {
    val theRest = clusterNodeIds - from
    theRest.foreach(id => tell(id, raftRequest))
  }
}
