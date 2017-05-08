package jabroni.rest.test

import cucumber.api.{DataTable, PendingException}
import cucumber.api.scala.{EN, ScalaDsl}
import miniraft.state._
import org.scalatest.Matchers

class RaftSteps extends ScalaDsl with EN with Matchers with TestData {

  import RaftSteps._

  var state = RaftTestState()
  Given("""^Node (.*) with state$""") { (nodeId: NodeId, stateTable: DataTable) =>
    val List(stateRow) = stateTable.toMap
    val node = raftNodeForRow(stateRow)
    state = state.withNode(nodeId, node)
  }
  When("""^Node (.*) has an election timeout$""") { (nodeId: NodeId) =>
    state = state.electionTimeout(nodeId)
  }
  Then("""^Node (.*) should send the RequestVote messages$""") { (nodeId: NodeId, messagesTable: DataTable) =>
    messagesTable.toMap.map { msgRow =>
      requestVoteForRow(msgRow)
    }
    state = state.electionTimeout(nodeId)
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
  When("""^Node (.*) receives its RequestVote message, it should reply with$""") { (nodeId: NodeId, responseTable: DataTable) =>
    val List(row) = responseTable.toMap
    val toNode = row("to node")
    val resp = state.requestVoteResponseMessagesTo(toNode)
    resp.term shouldBe Term(row("term").toInt)
    resp.granted shouldBe row("granted").toBoolean
  }
  When("""^Node (.*) receives its responses$""") { (nodeId: NodeId) =>
    state.applyResponsesTo(nodeId)
  }
  Then("""^Node (.*) should send the empty AppendEntities messages$""") { (nodeId: NodeId, appendTable: DataTable) =>

    appendTable.toMap.map { row =>

      //      val values = row("entries").split(",\\s+", -1).filterNot(_.isEmpty).toList
      //      val entries = values.map { x =>
      //        LogEntry(Term(1), )
      //      }
      //      AppendEntries(
      //        term = Term(row("term").toInt),
      //        leaderId = row("leader id"),
      //        prevLogIndex = row("prev log index").toInt,
      //        prevLogTerm = Term(row("prev log term").toInt),
      //        entries: List[LogEntry] = Nil,
      //      leaderCommit: LogIndex
      //      )

      row("to node")
      row("leader commit")
      row("term")
      row("leader id")
      row("prev log index")
      row("prev log term")
      row("leader commit")

    }
  }
  When("""^Node (.*) receives its append entities message, it should reply with$""") { (nodeId: NodeId, appendEntitiesTable: DataTable) =>


    //// Write code here that turns the phrase above into concrete actions
    throw new PendingException()

  }
  Then("""^no messages should be pending$""") {
    () =>
      //// Write code here that turns the phrase above into concrete actions
      throw new PendingException()
  }
  Given("""^Node A has the cluster view$""") {
    (arg0: DataTable) =>
      //// Write code here that turns the phrase above into concrete actions
      throw new PendingException()
  }
  When("""^Node A receives a client request to add foo$""") {
    () =>
      //// Write code here that turns the phrase above into concrete actions
      throw new PendingException()
  }
  Then("""^The log for Node A should be$""") {
    (arg0: DataTable) =>
      //// Write code here that turns the phrase above into concrete actions
      throw new PendingException()
  }
  Then("""^Node A should send the AppendEntries message$""") {
    (arg0: DataTable) =>
      //// Write code here that turns the phrase above into concrete actions
      throw new PendingException()
  }
  When("""^Node B receives its AppendEntries message, it should reply with$""") {
    (arg0: DataTable) =>
      //// Write code here that turns the phrase above into concrete actions
      throw new PendingException()
  }
  Then("""^The log for Node B should be$""") {
    (arg0: DataTable) =>
      //// Write code here that turns the phrase above into concrete actions
      throw new PendingException()
  }


}

object RaftSteps {

  def expectedClusterViewForTable(viewTable: List[Map[String, String]]): List[(String, NodeView, Boolean)] = {
    viewTable.map { row =>
      val peerId = row("peer")

      val nodeView = NodeView(
        nextIndex = row("next index").toInt,
        matchIndex = row("match index").toInt)

      val voteGranted = row("vote granted").toBoolean

      (peerId, nodeView, voteGranted)
    }
  }

  def roleForString(name: String): NodeRole = {
    name match {
      case "Follower" => Follower
      case "Leader" => Leader
      case "Candidate" => Candidate
    }
  }

  def raftNodeForRow(row: Map[String, String]): RaftNode = {
    val term = Term(row("current term").toInt)
    val role = roleForString(row("state"))
    val votedFor = Option(row("voted for"))
    val commitIndex = row("commit index").toInt
    val lastApplied = row("last applied").toInt
    RaftNode(role, PersistentState(term, votedFor, Log()), VolatileState(commitIndex, lastApplied))
  }

  def requestVoteForRow(row: Map[String, String]) = {
    row("to node")
    row("term")
    row("last log index")
    row("last log term")
    ???
  }
}