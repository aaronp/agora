package miniraft.state.rest

import io.circe.parser._
import io.circe.syntax._
import agora.rest.BaseSpec
import agora.rest.test.BufferedTransport
import miniraft.state._
import miniraft.state.rest.NodeStateSummary.LeaderSnapshot

import scala.concurrent.ExecutionContext

class NodeStateSummaryTest extends BaseSpec {

  "NodeStateSumary" should {
    "unmarshal" in {
      val json =
        """{
          |  "summary" : {
          |    "id" : "localhost:9001",
          |    "clusterSize" : 5,
          |    "term" : 6,
          |    "role" : "leader",
          |    "votedFor" : "localhost:9001",
          |    "lastCommittedIndex" : 0,
          |    "lastUnappliedIndex" : 0,
          |    "lastLogTerm" : 0,
          |    "electionTimer" : "election timer",
          |    "heartbeatTimer" : "heartbeat timer"
          |  },
          |  "clusterViewByNodeId" : {
          |    "localhost:9003" : {
          |      "matchIndex" : 0,
          |      "nextIndex" : 1
          |    },
          |    "localhost:9004" : {
          |      "matchIndex" : 0,
          |      "nextIndex" : 1
          |    },
          |    "localhost:9002" : {
          |      "matchIndex" : 0,
          |      "nextIndex" : 1
          |    }
          |  }
          |}""".stripMargin

      val Right(LeaderSnapshot(_, view)) = decode[NodeStateSummary](json)

      view.size shouldBe 3

    }
  }
  "NodeStateSummary json encoding" should {
    "marshal/unmarshal a follower node" in {
      val nss              = summaryForRole(Follower)
      val Right(backAgain) = decode[NodeStateSummary](nss.asJson.noSpaces)
      nss shouldBe backAgain
    }

    "marshal/unmarshal a candidate node" in {
      val nss              = summaryForRole(Candidate(5, Set("got for it"), Set("i hate you")))
      val Right(backAgain) = decode[NodeStateSummary](nss.asJson.noSpaces)
      nss shouldBe backAgain
    }

    "marshal/unmarshal a leader node" in {
      val nss              = summaryForRole(Leader(Map("foo" -> ClusterPeer(4, 5))))
      val json             = nss.asJson
      val Right(backAgain) = decode[NodeStateSummary](json.noSpaces)
      nss shouldBe backAgain
    }
  }

  def summaryForRole(role: NodeRole) = {
    import ExecutionContext.Implicits.global

    val ps = PersistentState[String]() { _ =>
      }

    NodeStateSummary(RaftNodeLogic("someId", RaftState(role, ps)), new BufferedTransport("someId")).futureValue
  }

}
