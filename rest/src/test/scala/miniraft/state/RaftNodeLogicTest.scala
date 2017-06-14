package miniraft.state

import agora.rest.BaseSpec
import miniraft.UpdateResponse
import org.scalatest.concurrent.Eventually

class RaftNodeLogicTest extends BaseSpec with Eventually {

  "RaftNode election timeout" should {
    "appoint a single leader in a 3 node cluster" in {
      withDir { dir =>
        val clusterById = TestCluster.under(dir).of[String]("A", "B", "C") { applyToStateMachine =>
          // not needed
          ???
        }
        val a = clusterById("A")
        val b = clusterById("B")
        val c = clusterById("C")

        def verifyLeader(expectedLeader: TestCluster.TestClusterNode[String], expectedTerm: Int) = {
          val nodeLeaderState = eventually {
            val state = expectedLeader.asyncNode.state.futureValue
            state.summary.role shouldBe "leader"
            state
          }
          val theOtherNodeIds                              = clusterById.keySet - expectedLeader.id
          val expectedLeaderView: Map[String, ClusterPeer] = theOtherNodeIds.map(_ -> ClusterPeer(0, 0)).toMap
          expectedLeader.logic.leaderState.get shouldBe expectedLeaderView

          nodeLeaderState.summary.term shouldBe Term(expectedTerm)
          theOtherNodeIds.foreach { id =>
            verifyFollower(clusterById(id), expectedTerm, expectedLeader.id)
          }
        }
        def verifyFollower(n: TestCluster.TestClusterNode[String], expectedTerm: Int, leaderId: NodeId) = {
          n.logic.isFollower shouldBe true
          n.logic.currentTerm shouldBe Term(expectedTerm)
          n.logic.leaderId shouldBe Option(leaderId)
        }

        a.asyncNode.forceElectionTimeout
        verifyLeader(a, 2)

        // now time-out b
        b.asyncNode.forceElectionTimeout
        verifyLeader(b, 3)

        c.asyncNode.forceElectionTimeout
        verifyLeader(c, 4)
      }
    }
  }
  "RaftNode.leaderApi.append" should {
    "append entries" in {

      withDir { dir =>
        val clusterById = TestCluster.under(dir).of[String]("A", "B", "C") { applyToStateMachine =>
          println("Applying " + applyToStateMachine)
        }
        val a = clusterById("A")
        val b = clusterById("B")
        val c = clusterById("C")

        a.asyncNode.forceElectionTimeout
        eventually {
          a.asyncNode.state.futureValue.summary.role shouldBe "leader"
        }

        println(a.asyncNode.state().futureValue)
        println(b.asyncNode.state().futureValue)
        println(c.asyncNode.state().futureValue)

        val api                    = a.node
        val result: UpdateResponse = a.leader("First append").futureValue
        val appended               = result.result.futureValue
        appended shouldBe true

        println(a.asyncNode.state().futureValue)
        println(b.asyncNode.state().futureValue)
        println(c.asyncNode.state().futureValue)

      }
    }
  }

}
