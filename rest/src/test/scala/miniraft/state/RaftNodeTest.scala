package miniraft.state

import agora.api.BaseSpec
import agora.rest.HasMaterializer
import miniraft.state.RaftNode.async
import miniraft.state.rest.NodeStateSummary.LeaderSnapshot
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future

class RaftNodeTest extends BaseSpec with Eventually with HasMaterializer {

  "RaftNode election timeout" should {
    "appoint a single leader in a 3 node cluster" in {
      withDir { dir =>
        val clusterById = TestCluster.under(dir).of[String]("A", "B", "C") {
          case (node, entry) => ??? // we don't send any appends
        }
        val a = clusterById("A")
        val b = clusterById("B")
        val c = clusterById("C")

        def verifyLeader(expectedLeader: async.RaftNodeActorClient[String], expectedTerm: Int) = {
          val LeaderSnapshot(nodeLeaderState, leaderView) = eventually {
            val state = expectedLeader.state.futureValue
            state.summary.role shouldBe "leader"
            state
          }
          val theOtherNodeIds                              = clusterById.keySet - expectedLeader.id
          val expectedLeaderView: Map[String, ClusterPeer] = theOtherNodeIds.map(_ -> ClusterPeer(0, 1)).toMap

          leaderView shouldBe expectedLeaderView

          nodeLeaderState.term shouldBe Term(expectedTerm)
          theOtherNodeIds.foreach { id =>
            val node: async.RaftNodeActorClient[String] = clusterById(id)
            verifyFollower(node, expectedTerm, expectedLeader.id)
          }
        }

        def verifyFollower(n: async.RaftNodeActorClient[String], expectedTerm: Int, leaderId: NodeId) = {
          val summary = n.state.futureValue.summary
          summary.role shouldBe "follower"
          summary.term shouldBe Term(expectedTerm)
          summary.votedFor shouldBe Option(leaderId)
        }

        a.forceElectionTimeout
        verifyLeader(a, 2)

        // now time-out b
        b.forceElectionTimeout
        verifyLeader(b, 3)

        // ... and c
        c.forceElectionTimeout
        verifyLeader(c, 4)
      }
    }
  }
  "RaftNode.leaderApi.append" should {
    "append entries" in {

      import materializer.executionContext

      withDir { dir =>
        val clusterById = TestCluster.under(dir).of[String]("A", "B", "C") {
          case (node, entry) => println(s"\t\t>>>> $node applying $entry <<<<")
        }
        val a = clusterById("A")

        a.forceElectionTimeout
        eventually {
          a.state.futureValue.summary.role shouldBe "leader"
        }

        val appendAck = a.append("First append").futureValue
        val appended  = appendAck.result.futureValue
        appended shouldBe true

        val LeaderSnapshot(newLeaderSnapshot, newLeaderView) = a.state.futureValue
        newLeaderSnapshot.summary.lastUnappliedIndex shouldBe 1
        newLeaderView shouldBe Map("B" -> ClusterPeer(1, 2), "C" -> ClusterPeer(1, 2))

        var summaries = Future.sequence(clusterById.values.map(_.state())).futureValue.map(_.summary)
        summaries.foreach { s =>
          s.lastUnappliedIndex shouldBe 1
        }

        eventually {
          // we'll need to ack whichever nodes weren't in the original majority at some point
          a.forceHeartbeatTimeout

          summaries = Future.sequence(clusterById.values.map(_.state())).futureValue.map(_.summary)
          summaries.foreach { s =>
            s.lastCommittedIndex shouldBe 1
          }
        }
      }
    }
  }

}
