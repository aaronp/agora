package agora.rest.test

import akka.actor.{ActorRefFactory, ActorSystem}
import miniraft._
import miniraft.state.{NodeId, _}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{AppendedClues, Matchers}

import scala.reflect.ClassTag

object SentRequest {

  val VoteRequestHeader = List("type", "to node", "term", "last log index", "last log term")
  val AppendHeader      = List("type", "to node", "term", "leader id", "commit index", "prev log index", "prev log term", "entry")
}

case class SentRequest(from: NodeId, to: NodeId, req: RaftRequest) {

  import SentRequest._

  override def toString = {
    def fmt(header: List[String], rows: List[List[Any]]) = {
      val lens: List[List[Int]] = rows.map(_.map(_.toString.length))
      val maxLens: List[Int] = lens.transpose.map(_.max).zip(header).map {
        case (len, h) => h.length.max(len)
      }
      (List(header) ::: rows)
        .map { row =>
          row
            .zip(maxLens)
            .map {
              case (col, len) => col.toString.padTo(len, ' ')
            }
            .mkString(" | ")
        }
        .mkString("\n")
    }

    req match {
      case RequestVote(Term(term), candidateId, lastLogIndex, Term(lastLogTerm)) =>
        require(candidateId == from)
        val data = List("RequestVote", to, term, lastLogIndex, lastLogTerm)
        fmt(VoteRequestHeader, data :: Nil)
      case AppendEntries(Term(term), leaderId, commitIndex, prevLogIndex, Term(prevLogTerm), entries) =>
        val data: List[Any] = List("AppendEntries", to, term, leaderId, commitIndex, prevLogIndex, prevLogTerm, entries.getOrElse(""))
        fmt(AppendHeader, data :: Nil)
    }
  }
}

case class SentResponse(sent: SentRequest, resp: RaftResponse) {
  override def toString = {
    s"Reply to { \n${sent}\n${pprint.stringify(resp)}\n}\n"
  }
}

case class RaftTestState(nodes: List[TestNodeLogic], transportById: Map[NodeId, BufferedTransport], clientResponses: List[UpdateResponse] = Nil)
    extends Matchers
    with AppendedClues
    with ScalaFutures {

  def verifyLogForNode(nodeId: NodeId, expectedAndCommitted: List[(LogEntry[String], Boolean)]) = {
    withClue(s"\n${toString}\n") {
      assertLogForNode(nodeId, expectedAndCommitted)
    }
  }

  private def assertLogForNode(nodeId: NodeId, expectedAndCommitted: List[(LogEntry[String], Boolean)]) = {

    val node                   = serverForId(nodeId)
    val (committed, unapplied) = expectedAndCommitted.partition(_._2)
    if (unapplied.nonEmpty) {
      val expectedLastUnapplied = unapplied.map(_._1.index).max
      val actual                = node.lastUnappliedIndex
      actual shouldBe expectedLastUnapplied
    }
    if (committed.nonEmpty) {
      val expectedLastCommitted = committed.map(_._1.index).max
      node.logic.lastCommittedIndex shouldBe expectedLastCommitted
    }
    expectedAndCommitted.foreach {
      case (entry, committed) =>
        val actual: LogEntry[String] = node.logic.logEntryAt(entry.index).get
        actual shouldBe entry
    }

  }

  private implicit lazy val leaderSystem: ActorRefFactory = ActorSystem("leader")

  def onClientRequest(nodeId: String, input: String): RaftTestState = {

    val raftNode: TestNodeLogic = serverForId(nodeId)
    raftNode.leaderId match {
      case None => fail(s"Node $nodeId doesn't know who the leader is: $toString")
      case Some(`nodeId`) =>
        import scala.concurrent.ExecutionContext.Implicits._
        val resp = raftNode.append(input)(global)
        copy(clientResponses = resp :: clientResponses)
      case Some(other) =>
        onClientRequest(other, input)
    }
  }

  override def toString = {
    nodes
      .sortBy(_.id)
      .map { state =>
        val tns = transportById(state.id)
        s"""$state
           |
         |$tns
     """.stripMargin
      }
      .mkString("\n")
  }

  def clusterSize = nodes.size

  def flushAppendEntityMessageTo(toNode: NodeId): RaftTestState = {
    flushRequest(findSingleRequestTo[AppendEntries[String]](toNode))
  }

  /**
    * Actually apply the response messages which have been sent to 'nodeId' to that server
    * (e.g. allow pending messages to go through)
    *
    * @param nodeId
    */
  def flushResponsesTo(nodeId: NodeId) = {
    val server = serverForId(nodeId)
    val tns    = transportById(nodeId)

    tns.flushReplies.foreach { r =>
      server.logic.onResponse(r.sent.to, r.resp, tns)
    }
    this
  }

  def flushRequestsTo(nodeId: NodeId) = {
    val pendingSent = pendingRequests.filter(_.to == nodeId)
    pendingSent.foreach(flushRequest)
    this
  }

  def flushRequestsFrom(nodeId: NodeId) = {
    val pendingSent = pendingRequests.filter(_.from == nodeId)
    pendingSent.foreach(flushRequest)
    this
  }

  def flushRequests(messages: List[SentRequest]): RaftTestState = messages.foldLeft(this)(_ flushRequest _)

  def flushRequest(r: SentRequest) = {
    require(pendingRequests.contains(r))
    val tns = transportById(r.from)
    tns.removeRequest(r)

    val svr      = serverForId(r.to)
    val response = svr.logic.onRequest(r.req, transportById(r.to))
    tns += SentResponse(r, response)
    this
  }

  def flushRequests() = pendingRequests.foreach(flushRequest)

  def flushReplies() = nodeIds.foreach(flushResponsesTo)

  def flushMessages(limit: Int = 10): Unit = {
    flushRequests()
    flushReplies()
    if (pendingRequests.nonEmpty || pendingReplies.nonEmpty) {
      require(limit > 0, "Message loop detected")
      flushMessages(limit - 1)
    }
  }

  def verifyHasResponse(sender: NodeId, responseFrom: NodeId, resp: RequestVoteResponse) = {

    val found = transportById(sender).replies.filter {
      case SentResponse(SentRequest(_, to, _), _) => to == responseFrom
    }

    {
      found.size shouldBe 1
    } withClue (s"${sender} responses from ${responseFrom} :: $found\n$this")
  }

  def pendingRequests: List[SentRequest] = transportById.values.flatMap(_.pendingRequests).toList

  def pendingReplies = transportById.values.flatMap(_.replies).toList

  def verifyPendingSentMessages(expectedSentMessages: List[SentRequest]) = {
    pendingRequests shouldBe expectedSentMessages
    this
  }

  def verifyAppendEntries(fromNode: NodeId, messagesByReceiver: Map[String, AppendEntries[String]]): RaftTestState = {

    messagesByReceiver.foreach {
      case (sentTo, expectedMsg) =>
        val List(only) = appendEntryMessagesSent(fromNode, sentTo)
        withClue(s"\n$toString\n") {
          only shouldBe expectedMsg
        }
    }
    this
  }

  def appendEntryMessagesSent(fromNode: NodeId, toNode: NodeId): List[AppendEntries[String]] = {
    pendingRequests.collect {
      case SentRequest(`fromNode`, `toNode`, req: AppendEntries[_]) => req.asInstanceOf[AppendEntries[String]]
    }
  }

  def flushSingleRequestVoteSentTo(toNode: NodeId): RaftTestState = {
    flushRequest(findSingleRequestTo[RequestVote](toNode))
  }

  def findSingleRequestTo[T: ClassTag](toNode: NodeId): SentRequest = {
    val c1ass = implicitly[ClassTag[T]].runtimeClass
    val pr    = pendingRequests
    val messages: List[SentRequest] = pr.collect {
      case req @ SentRequest(_, `toNode`, msg) if c1ass == msg.getClass => req
    }
    messages match {
      case List(only) => only
      case many       => sys.error(s"There were ${many.size} messages sent to $toNode: $many\n$this")
    }
  }

  def verifyNodeState(nodeId: String, expected: RaftState[String]): Unit = {
    withClue(s"\n===== EXPECTED ======\n\n${expected}\n\n===== ACTUAL ====\n${toString}\n\n=================\n\n") {
      val actual = serverForId(nodeId).logic.raftState
      actual shouldBe expected
    }
  }

  def verifyClusterView(nodeId: NodeId, expectations: List[(NodeId, ClusterPeer, Boolean)]): Unit = {

    val state                               = serverForId(nodeId).logic.leaderState.get
    val actual: List[(NodeId, ClusterPeer)] = state.toList
    val expected = expectations.map {
      case (a, b, _) => (a, b)
    }
    actual.sortBy(_._1) shouldBe expected.sortBy(_._1)
  }

  def electionTimeout(nodeId: NodeId): RaftTestState = {
    val t   = transportById(nodeId)
    val svr = serverForId(nodeId)
    svr.logic.onElectionTimeout(t)
    this
  }

  def serverForId(nodeId: NodeId): TestNodeLogic = {
    nodes.find(_.id == nodeId).getOrElse(sys.error(s"Couldn't find $nodeId: \n$this"))
  }

  def nodeIds = nodes.map(_.id).toSet

  def withNode(nodeId: NodeId, initialState: RaftState[String]): RaftTestState = {
    require(!nodes.map(_.id).contains(nodeId), s"Node $nodeId is already in the test cluster")

    val protocol: BufferedTransport                      = new BufferedTransport(nodeId, transportById.keySet + nodeId)
    val newTestNode: TestCluster.TestClusterNode[String] = TestCluster.nodeForState(nodeId, initialState, protocol)

    val newTransportById = transportById.updated(nodeId, protocol)

    val clusterIds = newTransportById.keySet
    println(clusterIds)

    val newNodes: List[TestNodeLogic] = newTestNode :: nodes
    newNodes.size shouldBe newTransportById.size
    //    val newCluster: Map[NodeId, RaftEndpoint[String]] = newNodes.map(n => n.id -> n.endpoint).toMap
    newNodes.foreach(_.protocol.updateClusterView(newTransportById.keySet))

    //    ss.protocol.update(newCluster)
    //    ss.protocol.updateHandler {
    //      case (id, _, _, resp) => ss.logic.onResponse(id, resp, ss.protocol)
    //    }

    val newSize = newNodes.size
    newNodes.foreach { member =>
      member.protocol.clusterSize shouldBe newSize
    }

    copy(nodes = newNodes, transportById = newTransportById)
  }
}
