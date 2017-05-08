package jabroni.rest.test

import miniraft.state.{NodeId, RaftRequest, RaftResponse, _}
import org.scalatest.Matchers

case class RequestResponse(from: NodeId, to: NodeId, req: RaftRequest, resp: RaftResponse)

case class RaftTestState(nodes: List[ServerState] = Nil,
                         messages: List[RequestResponse] = Nil)
  extends Matchers {
  def applyResponsesTo(nodeId: NodeId) = {
    val responses = messages.collect {
      case RequestResponse(_, `nodeId`, _, resp) => resp
    }
    require(responses.nonEmpty, s"no respnses to $nodeId: $messages")
    val server = serverForId(nodeId)
    responses.foreach {
      case append: AppendEntriesResponse => server.onAppendEntriesResponse(append)
      case vote: RequestVoteResponse => server.onRequestVoteResponse(vote)
    }
  }


  def requestVoteResponseMessagesTo(toNode: NodeId) = {
    messages.find(_.to == toNode).get.resp.asInstanceOf[RequestVoteResponse]
  }


  def verifyNodeState(nodeId: String, expected: RaftNode): Unit = {
    serverForId(nodeId).raftNode shouldBe expected
  }

  def verifyClusterView(nodeId: NodeId, expectations: List[(NodeId, NodeView, Boolean)]): Unit = {

    val state: LeaderState = serverForId(nodeId).leaderState.get
    val actual: List[(NodeId, NodeView)] = state.viewById.toList
    val expected = expectations.map {
      case (a, b, _) => (a, b)
    }
    actual.sortBy(_._1) shouldBe expected.sortBy(_._1)
  }

  case class NodeTransport(from: NodeId) extends Transport {
    var messages: List[RequestResponse] = Nil

    override def tell(id: NodeId, raftRequest: RaftRequest): Unit = {
      val response = serverForId(id).receive(raftRequest)
      messages = RequestResponse(from, id, raftRequest, response) :: messages
      serverForId(id).onResponse(response)
    }

    override def tellOthers(raftRequest: RaftRequest): Unit = {
      val theRest = nodeIds - from
      theRest.foreach(id => tell(id, raftRequest))
    }
  }

  def electionTimeout(nodeId: NodeId): RaftTestState = {
    val t = NodeTransport(nodeId)
    serverForId(nodeId).onElectionTimeout(t)
    copy(messages = t.messages ++ messages)
  }

  def serverForId(nodeId: NodeId): ServerState = nodes.find(_.id == nodeId).get

  def nodeIds = nodes.map(_.id).toSet

  def withNode(nodeId: NodeId, node: RaftNode): RaftTestState = {
    val ss = new ServerState(nodeId).withNode(node)
    copy(nodes = ss :: nodes)
  }

}
