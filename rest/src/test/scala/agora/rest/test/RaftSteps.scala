package agora.rest.test

import cucumber.api.scala.{EN, ScalaDsl}
import cucumber.api.{DataTable, Scenario}
import miniraft.state._
import miniraft.{AppendEntries, RequestVote, RequestVoteResponse}
import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

/**
  * For node states, see [[RaftSteps]]#roleForString(String)
  */
class RaftSteps extends ScalaDsl with EN with Matchers with TestData with ScalaFutures with Eventually {

  import RaftSteps._

  var state = RaftTestState(Nil, Map.empty)

  /**
    * @see [[roleForString]] for role parsing
    */
  Given("""^Node (.*) with state$""") { (nodeId: NodeId, stateTable: DataTable) =>
    val List(stateRow) = stateTable.toMap
    val node           = raftNodeForRow(stateRow)
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

  // TODO - remove, as we can assert this from the 'should be in state' step which encodes the view into the leader state
  Then("""^Node (.*) should have cluster view$""") { (nodeId: String, viewTable: DataTable) =>
    val expected = expectedClusterViewForTable(viewTable.toMap)
    state.verifyClusterView(nodeId, expected)
  }
  Then("""^Node (.*) should be in state$""") { (nodeId: String, stateTable: DataTable) =>
    val List(stateRow) = stateTable.toMap
    val expected       = raftNodeForRow(stateRow)
    state.verifyNodeState(nodeId, expected)

  }
  When("""^Node (.*) receives its RequestVote message, it should reply with$""") { (receivingNode: NodeId, responseTable: DataTable) =>
    val List(row) = responseTable.toMap
    val toNode    = row("to node")

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
  Then("""^no messages should be pending$""") { () =>
    withClue(state.toString) {
      state.pendingRequests shouldBe empty
    }
  }
  When("""^Node (.*) receives a client request to add (.*)$""") { (nodeId: String, value: String) =>
    state = state.onClientRequest(nodeId, value)
  }
  Then("""^The log for Node (.*) should be$""") { (nodeId: String, logTable: DataTable) =>
    val expected = logTable.toMap.map(logFromTableRow)
    state.verifyLogForNode(nodeId, expected)
  }
  When("""^Node (.*) receives its AppendEntries message, it should reply with$""") { (nodeId: String, appendEntriesReplyTable: DataTable) =>
    state = state.flushAppendEntityMessageTo(nodeId)
  }
  Then("""^Node (.*) should send the AppendEntries messages?$""") { (nodeId: String, appendTable: DataTable) =>
    val messagesByReceiver: List[(String, AppendEntries[String])] = appendTable.toMap.map { row =>
      val receiver = row("to node")
      val req = AppendEntries[String](
        Term(row("term").toInt),
        leaderId = row("leader id"),
        commitIndex = row("commit index").toInt,
        prevLogIndex = row("prev log index").toInt,
        prevLogTerm = Term(row("prev log term").toInt),
        row.get("entries").filterNot(_.isEmpty)
      )

      receiver -> req
    }

    val byReceiver = messagesByReceiver.toMap.ensuring(_.size == messagesByReceiver.size)
    state.verifyAppendEntries(fromNode = nodeId, byReceiver)

  }

  Before { (scen: Scenario) =>
    state = RaftTestState(Nil, Map.empty)
  }

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(30.seconds.toSeconds, Seconds)), interval = scaled(Span(500, Millis)))
}

object RaftSteps {

  def logFromTableRow(row: Map[String, String]) = {
    LogEntry[String](Term(row("term").toInt), row("index").toInt, row("value")) -> row("committed").toBoolean
  }

  def expectedClusterViewForTable(viewTable: List[Map[String, String]]): List[(String, ClusterPeer, Boolean)] = {
    viewTable.map { row =>
      val peerId = row("peer")

      val nodeView = ClusterPeer(matchIndex = row("match index").toInt, row("next index").toInt)

      val voteGranted = row("vote granted").toBoolean

      (peerId, nodeView, voteGranted)
    }
  }

  def raftNodeForRow(row: Map[String, String]): RaftState[String] = {
    val term     = Term(row("current term").toInt)
    val role     = roleForString(row("state"))
    val votedFor = Option(row("voted for").trim).filterNot(_.isEmpty)

    val appendIndex = row("append index").toInt
    val lastApplied = row("last applied").toInt
    val unapplied = (1 to appendIndex).map { i =>
      val logTerm = row.get("log term").map(t => Term(t.toInt)).getOrElse(term)
      LogEntry(logTerm, i, s"entry $i")
    }
    var applied = List[LogEntry[String]]()
    val ps      = PersistentState[String](term, votedFor)(e => applied = e :: applied).append(unapplied.toList)
    val committedState = (1 to lastApplied).foldLeft(ps) {
      case (state, index) =>
        state.commit(index)
        state
    }
    val rs = RaftState[String](role, committedState)

    rs.role match {
      case Candidate(c) =>
        val me = rs.persistentState.votedFor
          .getOrElse(
            sys.error(s"Invalid step config: candidates always have to vote for themselves. rs.persistentState.votedFor was ${rs.persistentState.votedFor}"))
        require(
          c.votedFor.contains(me),
          "Attempt to put a candidate node in an invalid state, " +
            s"s candidates always vote for themselves :${c.votedFor} doen't contain ${me}"
        )
      case _ =>
    }

    require(rs.lastCommittedIndex == lastApplied)
    require(rs.lastUnappliedIndex == appendIndex)
    rs
  }

  /**
    * Expects strings in the form:
    *
    * $ Leader(<peer-nodes>)
    * $ Candidate(votesFor:[<for-votes>], votesAgainst:[<against-votes>], <cluster size>)
    * $ Follower
    *
    * where <peer-nodes> iis of the form <nodeId>:<matchIndex>,<nextIndex> used to expect a cluster view from the leader
    * and '<for-votes>' and '<against-votes>' are comma-separated node IDs who voted for/against the candidate.
    *
    * @param name
    * @return
    */
  def roleForString(name: String): NodeRole = {
    val CandidateR   = """Candidate\(\s*votesFor\s*:\s*\[(.*)\],\s*votesAgainst\s*:\s*\[(.*)\],\s*(\d+)\)""".r
    val LeaderR      = """Leader\(\s*(.*)\)""".r
    val ClusterViewR = """(.*):(.*),(.*)""".r
    val newRole = name match {
      case "Follower" => Follower
      case LeaderR("") =>
        Leader(Map.empty)
      case LeaderR(clusterView) =>
        val viewByNodeId: Array[(String, ClusterPeer)] = clusterView.split("\\s*;\\s*", -1).map {
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
        val counter: ElectionCounter = ids(votesAgainst).foldLeft(forCounter) {
          case (cntr, id) =>
            cntr.onVote(id, false)
            cntr
        }
        Candidate(counter)
      case other => sys.error(s"You've ballsed-up the feature state: '$other'")
    }
    newRole
  }

  def requestVoteForRow(fromNode: NodeId, row: Map[String, String]): SentRequest = {
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
