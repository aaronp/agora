package miniraft.state

import java.util.UUID

import agora.api.BaseSpec
import agora.rest.test.TestTimer
import miniraft._
import org.scalatest.concurrent.Eventually

import scala.reflect.ClassTag

class RaftNodeLogicTest extends BaseSpec with Eventually {

  "RaftNodeLogic onElectionTimeout / onVoteRequest" should {
    "elect a node" in {
      val scenario = new TwoNodeScenario
      scenario.makeNodeATheLeader
    }
  }

  "RaftNodeLogic append" should {
    "append an entry" in {
      val scenario = (new TwoNodeScenario).makeNodeATheLeader
      scenario.appendFirstEntry
    }
    "append subsequent entries before the first one is committed" in {
      val scenario = (new TwoNodeScenario).makeNodeATheLeader
      scenario.appendTwoUnconfirmedEntries
    }
    "append two entries after an election timeout" ignore {
      ???
    }
  }

  class TwoNodeScenario {
    val a = newNode("A")
    val b = newNode("B")

    def applyAppendToB(data: String, forB: AppendEntries[String]): AppendEntriesResponse = {
      withCluster("B") { c =>
        val response: AppendEntriesResponse = b.onAppendEntries(forB, c)

        response shouldBe AppendEntriesResponse(Term(2), true, 1)

        c.electionTimer.resetCalls.size shouldBe 1
        c.heartbeatTimer.cancelCalls shouldBe 0

        b.lastUnappliedIndex shouldBe 1
        b.lastCommittedIndex shouldBe 0
        b.logEntryAt(1) shouldBe Option(LogEntry(Term(2), 1, data))
        a.isCommitted(1) shouldBe false

        response
      }
    }

    def verifyConfirmResponse(confirmResp: AppendEntriesResponse) = {
      withCluster("A") { c =>
        val Leader(beforeView) = a.raftState.role
        beforeView shouldBe Map("B" -> ClusterPeer(1, 2))

        a.onAppendEntriesResponse("B", confirmResp, c)

        val Leader(afterView) = a.raftState.role
        afterView shouldBe beforeView

        c.told shouldBe empty

      }
    }

    /**
      * Exercises the lifecycle of an AppendEntry exchange between A and B in this two node cluster:
      *
      * 1) A receives an 'append' request from some presumed client call
      * 2) A applies the request to its log and sends an AppendEntries request
      * 3) B receives the request and updates its logs, then responds to A
      * 4) A receives the AppendEntriesResponse and, now having a majority of logs containing the data, commits it
      * 5) A sends out an empty AppendEntries request
      * 6) B receives the AppendEntries request and then commits its
      */
    def appendFirstEntry() = {
      val data = "first entry"

      // A sends B the first entry to append, having already applied it to its own log (uncommitted)
      val (forB, clientResponse) = createFirstAppendToB(data)

      // B appends the entry (uncommitted) and replies to A
      val appendResponseToA: AppendEntriesResponse = applyAppendToB(data, forB)

      // A now has a majority of the cluster w/ the entry, so it commits it
      // and sends out another Append as a HB message
      val appendConfirmToB: AppendEntries[String] = commitOnAppendResponseAndSendConfirmation(data, appendResponseToA)
      clientResponse.result.futureValue shouldBe true

      // B has an uncommitted entry -- this next empty 'AppendEntry' request will result in the entry being committed
      val confirmResp = applyEmptyCommitAppendEntriesRequest(data, appendConfirmToB)

      // actually applying the confirmation to A doesn't change A ... we're just happy they're happy
      verifyConfirmResponse(confirmResp)
    }

    def appendTwoUnconfirmedEntries() = {
      val firstData = "one"

      // A sends B the first entry to append, having already applied it to its own log (uncommitted)
      val (firstAppendRequest, firstClientResponse) = createFirstAppendToB(firstData)

      // B appends the entry (uncommitted) and replies to A
      val firstResponseToA: AppendEntriesResponse = applyAppendToB(firstData, firstAppendRequest)

      // A sends B the second entry to append, having already applied it to its own log (uncommitted)
      val secondData                                  = "second"
      val (secondAppendRequest, secondClientResponse) = createSecondAppendToB(firstData, secondData)

      // B appends the entry (uncommitted) and replies to A
//      val secondResponseToA: AppendEntriesResponse = applyAppendToB(firstData, firstAppendRequest)
    }

    def applyEmptyCommitAppendEntriesRequest(data: String, appendConfirmToB: AppendEntries[String]) = {
      withCluster("B") { c =>
        b.lastCommittedIndex shouldBe 0
        val confResponse = b.onAppendEntries(appendConfirmToB, c)

        // confirm the new committed state of b
        b.lastUnappliedIndex shouldBe 1
        b.lastCommittedIndex shouldBe 1
        b.logEntryAt(1) shouldBe Option(LogEntry(Term(2), 1, data))
        b.isCommitted(1) shouldBe true

        // confirm the response
        confResponse.matchIndex shouldBe 1
        confResponse.success shouldBe true
        confResponse.term shouldBe Term(2)

        confResponse
      }
    }

    def commitOnAppendResponseAndSendConfirmation(data: String, appendResponseToA: AppendEntriesResponse) = {
      withCluster("A") { c =>
        a.onAppendEntriesResponse("B", appendResponseToA, c)

        val Leader(view) = a.raftState.role
        view shouldBe Map("B" -> ClusterPeer(1, 2))

        a.lastUnappliedIndex shouldBe 1
        a.lastCommittedIndex shouldBe 1
        a.logEntryAt(1) shouldBe Option(LogEntry(Term(2), 1, data))
        a.isCommitted(1) shouldBe true

        val appendSentToB = c.onlyMessage[AppendEntries[String]]
        appendSentToB.entry shouldBe None
        appendSentToB.term shouldBe Term(2)
        appendSentToB.leaderId shouldBe "A"
        appendSentToB.commitIndex shouldBe 1
        appendSentToB.prevLogTerm shouldBe Term(2)
        appendSentToB.prevLogIndex shouldBe 1

        appendSentToB
      }
    }

    def createFirstAppendToB(data: String): (AppendEntries[String], UpdateResponse.Appendable) = {
      import scala.concurrent.ExecutionContext.Implicits.global
      withCluster("A") { c =>
        val clientResponse = a.add(data, c)
        clientResponse.result.isCompleted shouldBe false

        a.lastUnappliedIndex shouldBe 1
        a.lastCommittedIndex shouldBe 0
        a.logEntryAt(1) shouldBe Option(LogEntry(Term(2), 1, data))
        a.isCommitted(1) shouldBe false

        val appendSentToB = c.onlyMessage[AppendEntries[String]]
        appendSentToB.entry shouldBe Option(data)
        appendSentToB.term shouldBe Term(2)
        appendSentToB.leaderId shouldBe "A"
        appendSentToB.commitIndex shouldBe 0
        appendSentToB.prevLogTerm shouldBe Term(0)
        appendSentToB.prevLogIndex shouldBe 0

        appendSentToB -> clientResponse
      }
    }

    def createSecondAppendToB(first: String, second: String): (AppendEntries[String], UpdateResponse.Appendable) = {
      import scala.concurrent.ExecutionContext.Implicits.global
      withCluster("A") { c =>
        val clientResponse = a.add(second, c)
        clientResponse.result.isCompleted shouldBe false

        a.lastUnappliedIndex shouldBe 2
        a.lastCommittedIndex shouldBe 0
        a.logEntryAt(1) shouldBe Option(LogEntry(Term(2), 1, first))
        a.logEntryAt(2) shouldBe Option(LogEntry(Term(2), 2, second))
        a.isCommitted(1) shouldBe false
        a.isCommitted(2) shouldBe false

        val appendSentToB = c.onlyMessage[AppendEntries[String]]
        appendSentToB.entry shouldBe Option(second)
        appendSentToB.term shouldBe Term(2)
        appendSentToB.leaderId shouldBe "A"
        appendSentToB.commitIndex shouldBe 0
        appendSentToB.prevLogTerm shouldBe Term(2)
        appendSentToB.prevLogIndex shouldBe 1

        appendSentToB -> clientResponse
      }
    }

    def makeNodeATheLeader() = {
      val voteForA = withCluster("A") { c =>
        a.onElectionTimeout(c)
        a.raftState.role.name shouldBe "candidate"
        val List(requestVote: RequestVote) = c.told("B")
        requestVote.term shouldBe Term(2)
        requestVote.candidateId shouldBe "A"
        requestVote.lastLogIndex shouldBe 0
        requestVote.lastLogTerm shouldBe Term(0)
        requestVote
      }

      // initial vote
      val voteResponse: RequestVoteResponse = withCluster("B") { c =>
        val response = b.onVoteRequest(voteForA, c)
        c.told shouldBe empty
        response
      }

      voteResponse.granted shouldBe true
      voteResponse.term shouldBe Term(2)

      // vote response
      withCluster("A") { c =>
        a.onRequestVoteResponse("B", voteResponse, c)
        val List(("B", List(hbMsg))) = c.told.toList
        hbMsg shouldBe AppendEntries(Term(2), "A", 0, 0, Term(0), None)
      }
      this
    }
  }

  class TestCluster(ourId: NodeId, override val clusterNodeIds: Set[NodeId]) extends ClusterProtocol {
    var told = Map[NodeId, List[RaftRequest]]()

    def onlyMessage[T <: RaftRequest: ClassTag]: T = {
      val List((other, List(msg: T))) = told.toList
      require(other != ourId)
      msg
    }

    def clear() = told = Map[NodeId, List[RaftRequest]]()

    override def tell(id: NodeId, raftRequest: RaftRequest): Unit = {
      val newList = raftRequest :: told.getOrElse(id, Nil)
      told = told.updated(id, newList)
    }

    override def tellOthers(raftRequest: RaftRequest): Unit = (clusterNodeIds - ourId).foreach(tell(_, raftRequest))

    override val electionTimer  = new TestTimer
    override val heartbeatTimer = new TestTimer
  }

  def newNode(name: String) = {
    val dir = s"target/test/log-for-$name/${UUID.randomUUID()}".asPath.mkDirs()
    val ps = PersistentState(dir) { e: LogEntry[String] =>
      //println(s"$name applying $e to SM")
    }
    val state = RaftState(ps)
    RaftNodeLogic(name, state)
  }

  def withCluster[T](forNode: NodeId)(f: TestCluster => T): T = {
    val c = new TestCluster(forNode, Set("A", "B"))
    f(c)
  }

}
