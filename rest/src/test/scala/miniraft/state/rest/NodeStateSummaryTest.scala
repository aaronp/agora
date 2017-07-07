package miniraft.state.rest

import io.circe.parser._
import io.circe.syntax._
import agora.rest.BaseSpec
import agora.rest.test.BufferedTransport
import miniraft.state._
import miniraft.state.rest.NodeStateSummary.LeaderSnapshot

import scala.concurrent.ExecutionContext

class NodeStateSummaryTest extends BaseSpec {

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

    NodeStateSummary(RaftNodeLogic("someId", RaftState(role, ps)), new BufferedTransport("someId", Set("someId"))).futureValue
  }

}
