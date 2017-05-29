package jabroni.rest.test

import cucumber.api.scala.{EN, ScalaDsl}
import cucumber.api.{DataTable, PendingException}
import miniraft.state._
import org.scalatest.Matchers

class RaftSteps extends ScalaDsl with EN with Matchers with TestData {

  import RaftSteps._

  var state = RaftTestState(Nil, Map.empty)

  Given("""^Node (.*) with state$""") { (nodeId: NodeId, stateTable: DataTable) =>
    val List(stateRow) = stateTable.toMap
    val node = raftNodeForRow(stateRow)
    state = state.withNode(nodeId, node)
  }
  When("""^Node (.*) has an election timeout$""") { (nodeId: NodeId) =>
    state = state.electionTimeout(nodeId)
  }
  Then("""^Node (.*) should send the RequestVote messages?$""") { (nodeId: NodeId, messagesTable: DataTable) =>
    val expectedSentMessages = messagesTable.toMap.map { msgRow =>
      requestVoteForRow(nodeId, msgRow)
    }
    state = state.verifyPendingSentMessages(expectedSentMessages)
  }

  Then("""^Node (.*) should have cluster view$""") { (nodeId: String, viewTable: DataTable) =>
    val expected = expectedClusterViewForTable(viewTable.toMap)
    state.verifyClusterView(nodeId, expected)
  }
  Then("""^Node (.*) should be in state$""") { (nodeId: String, stateTable: DataTable) =>
    val List(stateRow) = stateTable.toMap
    val expected = raftNodeForRow(stateRow)
    state.verifyNodeState(nodeId, expected)

  }
  When("""^Node (.*) receives its RequestVote message, it should reply with$""") { (receivingNode: NodeId, responseTable: DataTable) =>
    val List(row) = responseTable.toMap
    val toNode = row("to node")

    state = state.flushSingleRequestVoteSentTo(receivingNode)
    val resp = RequestVoteResponse(
      Term(row("term").toInt),
      row("granted").toBoolean
    )
    state.verifyHasResponse(toNode, receivingNode, resp)
  }
  When("""^Node (.*) receives its responses$""") { (nodeId: NodeId) =>
    state = state.flushResponsesTo(nodeId)
  }
  Then("""^Node (.*) should send the empty AppendEntities messages?$""") { (nodeId: NodeId, appendTable: DataTable) =>

    val messagesByReceiver: List[(String, AppendEntries[String])] = appendTable.toMap.map { row =>
      val receiver = row("to node")
      val req = AppendEntries[String](
        Term(row("term").toInt),
        leaderId = row("leader id"),
        prevLogIndex = row("prev log index").toInt,
        prevLogTerm = Term(row("prev log term").toInt),
        Nil,
        leaderCommit = row("leader commit").toInt
      )

      receiver -> req
    }
    state.verifyAppendEntries(fromNode = nodeId,
      messagesByReceiver.toMap.ensuring(_.size == messagesByReceiver.size))

    state.flushRequestsFrom(nodeId)
  }
  When("""^Node (.*) receives its append entities message, it should reply with$""") { (nodeId: NodeId, appendEntitiesTable: DataTable) =>
    state = state.flushAppendEntityMessageTo(nodeId)
    ???
  }

  Then("""^no messages should be pending$""") { () =>
    state.pendingRequests shouldBe empty
  }
  When("""^Node (.*) receives a client request to add (.*)$""") { (nodeName: String, value: String) =>
    state = state.onClientRequest(nodeName, value)
  }
  Then("""^The log for Node (.*) should be$""") { (nodeName: String, clusterViewTable: DataTable) =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  When("""^Node (.*) receives its AppendEntries message, it should reply with$""") { (nodeName: String, appendEntriesReplyTable: DataTable) =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }
  Then("""^Node (.*) should send the AppendEntities messages?$""") { (nodeName: String, appendEntriesTable: DataTable) =>
    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()
  }

}

object RaftSteps {

  def expectedClusterViewForTable(viewTable: List[Map[String, String]]): List[(String, ClusterPeer, Boolean)] = {
    viewTable.map { row =>
      val peerId = row("peer")

      val nodeView = ClusterPeer(matchIndex = row("match index").toInt, row("next index").toInt)

      val voteGranted = row("vote granted").toBoolean

      (peerId, nodeView, voteGranted)
    }
  }


  def raftNodeForRow(row: Map[String, String]): RaftState[String] = {
    val term = Term(row("current term").toInt)
    val role = roleForString(row("state"))
    val votedFor = Option(row("voted for").trim).filterNot(_.isEmpty)

    val appendIndex = row("append index").toInt
    val lastApplied = row("last applied").toInt
    val unapplied = (1 to appendIndex).map { i =>
      LogEntry(term, i, s"entry $i")
    }
    val ps = PersistentState[String](term, votedFor).append(unapplied.toList)
    val committedState = (1 to lastApplied).foldLeft(ps) {
      case (state, index) =>
        state.commit(index)
        state
    }
    val rs = RaftState[String](role, committedState)

    rs.role match {
      case Candidate(c) =>
        val me = rs.persistentState.votedFor.getOrElse(sys.error("candidates always have to vote for themselves"))
        require(c.votedFor.contains(me), "Attempt to put a candidate node in an invalid state, " +
          s"s candidates always vote for themselves :${c.votedFor} doen't contain ${me}")
      case _ =>
    }

    require(rs.lastCommittedIndex == lastApplied)
    require(rs.lastUnappliedIndex == appendIndex)
    rs
  }


  def roleForString(name: String): NodeRole = {
    val CandidateR = """Candidate\(\s*votesFor\s*:\s*\[(.*)\],\s*votesAgainst\s*:\s*\[(.*)\],\s*(\d+)\)""".r
    val LeaderR = """Leader\(\s*(.*)\)""".r
    val ClusterViewR = """(.*):(.*),(.*)""".r
    val newRole = name match {
      case "Follower" => Follower
      case LeaderR(clusterView) =>
        val viewByNodeId = clusterView.split("\\s*;\\s*", -1).map {
          case ClusterViewR(nodeId, matchIndex, nextIndex) => nodeId -> ClusterPeer(matchIndex.toInt, nextIndex.toInt)
        }
        Leader(viewByNodeId.toMap.ensuring(_.size == viewByNodeId.size))
      case CandidateR(votesFor, votesAgainst, clusterSize) =>
        def ids(s: String) = s.split("\\s*,\\s*", -1).filterNot(_.isEmpty)

        val forCounter = ids(votesFor).foldLeft(ElectionCounterTest.create(clusterSize.toInt)) {
          case (cntr, id) =>
            cntr.onVote(id, true)
            cntr
        }
        val counter = ids(votesAgainst).foldLeft(forCounter) {
          case (cntr, id) =>
            cntr.onVote(id, false)
            cntr
        }
        Candidate(counter)
      case other => sys.error(s"You've ballsed-up the feature state: '$other'")
    }
    newRole
  }


  def requestVoteForRow(fromNode: NodeId, row: Map[String, String]) = {
    val to = row("to node")
    val msg = RequestVote(
      term = Term(row("term").toInt),
      candidateId = fromNode,
      lastLogIndex = row("last log index").toInt,
      lastLogTerm = Term(row("last log term").toInt)
    )
    SentRequest(fromNode, to, msg)
  }
}