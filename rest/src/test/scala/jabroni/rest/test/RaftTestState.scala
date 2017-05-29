package jabroni.rest.test

import miniraft.state.{NodeId, RaftRequest, RaftResponse, _}
import org.scalatest.{AppendedClues, Matchers}

import scala.reflect.ClassTag

object SentRequest {

  val VoteRequestHeader = List("type", "to node", "term", "last log index", "last log term")
  val AppendHeader = List("type", "to node", "term", "leader id", "prev log index", "prev log term", "leader commit")
}

case class SentRequest(from: NodeId, to: NodeId, req: RaftRequest) {

  import SentRequest._

  override def toString = {
    def fmt(header: List[String], rows: List[List[Any]]) = {
      val lens: List[List[Int]] = rows.map(_.map(_.toString.length))
      val maxLens: List[Int] = lens.transpose.map(_.max).zip(header).map {
        case (len, h) => h.length.max(len)
      }
      (List(header) ::: rows).map { row =>
        row.zip(maxLens).map {
          case (col, len) => col.toString.padTo(len, ' ')
        }.mkString(" | ")
      }.mkString("\n")
    }

    req match {
      case RequestVote(Term(term), candidateId, lastLogIndex, Term(lastLogTerm)) =>
        require(candidateId == from)
        val data = List("RequestVote", to, term, lastLogIndex, lastLogTerm)
        fmt(VoteRequestHeader, data :: Nil)
      case AppendEntries(Term(term), leaderId, prevLogIndex, Term(prevLogTerm), entries, leaderCommit) =>
        val data: List[Any] = List("AppendEntries", to, term, leaderId, prevLogIndex, prevLogTerm, leaderCommit)
        fmt(AppendHeader, data :: Nil)
    }
  }
}

case class SentResponse(sent: SentRequest, resp: RaftResponse) {
  override def toString = {
    s"Reply to { \n${sent}\n${pprint.stringify(resp)}\n}\n"
  }
}

class TestNode private(id: NodeId, raftNode: RaftState[String]) extends RaftNode[String](id, raftNode)

object TestNode {
  def apply(id: NodeId): TestNode = {
    val node = RaftState(PersistentState[String]())
    apply(id, node)
  }

  def apply(id: NodeId, node: RaftState[String]): TestNode = {
    new TestNode(id, node)
  }
}

case class RaftTestState(nodes: List[TestNode],
                         transportById: Map[NodeId, BufferedTransport]
                        )
  extends Matchers
    with AppendedClues {
  def onClientRequest(nodeId: String, input: String): RaftTestState = {

    this
  }


  override def toString = {
    nodes.map { state =>
      val tns = transportById(state.id)
      s"""$state
         |
         |$tns
     """.stripMargin
    }.mkString("\n")
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
    val server: TestNode = serverForId(nodeId)
    val tns = transportById(nodeId)

    tns.flushReplies.foreach { r =>
      server.onResponse(r.sent.to, r.resp, tns)
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

    val svr: TestNode = serverForId(r.to)
    val response = svr.onRequest(r.req, transportById(r.to))
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
        only shouldBe expectedMsg

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
    val pr = pendingRequests
    val messages: List[SentRequest] = pr.collect {
      case req@SentRequest(_, `toNode`, msg) if c1ass == msg.getClass => req
    }
    messages match {
      case List(only) => only
      case many => sys.error(s"There were ${many.size} messages sent to $toNode: $many\n$this")
    }
  }

  def verifyNodeState(nodeId: String, expected: RaftState[String]): Unit = {
    withClue(s"\n===== EXPECTED ======\n\n${expected}\n\n===== ACTUAL ====\n${toString}\n\n") {
      serverForId(nodeId).raftNode shouldBe expected
    }
  }

  def verifyClusterView(nodeId: NodeId, expectations: List[(NodeId, ClusterPeer, Boolean)]): Unit = {

    val state = serverForId(nodeId).leaderState.get
    val actual: List[(NodeId, ClusterPeer)] = state.toList
    val expected = expectations.map {
      case (a, b, _) => (a, b)
    }
    actual.sortBy(_._1) shouldBe expected.sortBy(_._1)
  }

  def electionTimeout(nodeId: NodeId): RaftTestState = {
    val t = transportById(nodeId)
    val svr = serverForId(nodeId)
    svr.onElectionTimeout(t)
    this
  }

  def serverForId(nodeId: NodeId): TestNode = {
    nodes.find(_.id == nodeId).getOrElse(sys.error(s"Couldn't find $nodeId: \n$this"))
  }

  def nodeIds = nodes.map(_.id).toSet

  def withNode(nodeId: NodeId, node: RaftState[String]): RaftTestState = {
    val ss = TestNode(nodeId, node)
    val clusterIds = transportById.keySet + nodeId
    val nt = new BufferedTransport(nodeId)
    val newTransport = transportById.updated(nodeId, nt).mapValues { nt =>
      nt.updateClusterView(clusterIds)
    }
    copy(nodes = ss :: nodes, transportById = newTransport)
  }
}
