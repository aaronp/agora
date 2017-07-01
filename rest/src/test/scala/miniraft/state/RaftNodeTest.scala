package miniraft.state

import agora.rest.{BaseSpec, HasMaterializer}
import miniraft.UpdateResponse
import miniraft.state.RaftNode.async
import miniraft.state.rest.NodeStateSummary.LeaderSnapshot
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future

class RaftNodeTest extends BaseSpec with Eventually with HasMaterializer {

  import materializer.executionContext

  "RaftNode election timeout" ignore {
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
          val expectedLeaderView: Map[String, ClusterPeer] = theOtherNodeIds.map(_ -> ClusterPeer(0, 0)).toMap

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

      withDir { dir =>
        val clusterById = TestCluster.under(dir).of[String]("A", "B", "C") {
          case (node, entry) => println(s"$node applying $entry")
        }
        val a = clusterById("A")

        a.forceElectionTimeout
        eventually {
          a.state.futureValue.summary.role shouldBe "leader"
        }

        val result: UpdateResponse = a.append("First append").futureValue
        val appended               = result.result.futureValue
        appended shouldBe true

        val LeaderSnapshot(newLeaderSnapshot, newLeaderView) = a.state.futureValue
        newLeaderSnapshot.summary.lastUnappliedIndex shouldBe 1
        newLeaderView shouldBe Map("B" -> ClusterPeer(1, 1), "C" -> ClusterPeer(1, 1))

        var summaries = Future.sequence(clusterById.values.map(_.state())).futureValue.map(_.summary)
        println(summaries.mkString("\n\n"))
        summaries.foreach { s =>
          s.lastUnappliedIndex shouldBe 1
        }

        eventually {
          summaries = Future.sequence(clusterById.values.map(_.state())).futureValue.map(_.summary)
          println(summaries.mkString("\n\n"))
          summaries.foreach { s =>
            s.lastCommittedIndex shouldBe 1
          }
        }
      }
    }
    "append multiple entries" ignore {

      withDir { dir =>
        var appliedMap = Map[NodeId, List[LogEntry[String]]]()
        object AppliedMapLock
        val clusterById = TestCluster.under(dir).of[String]("A", "B", "C") {
          case (node, entry) =>
            AppliedMapLock.synchronized {
              val newList = entry :: appliedMap.getOrElse(node, Nil)
              appliedMap = appliedMap.updated(node, newList)
            }
        }

        val c = clusterById("C")
        c.forceElectionTimeout

        eventually {
          c.state.futureValue.summary.role shouldBe "leader"
        }

        List("uno", "dos", "tres", "quatro").zipWithIndex.foreach {
          case (data, expectedIndex) =>
            c.append(data).futureValue.result.futureValue shouldBe true
            val summaries = Future.sequence(clusterById.values.map(_.state())).futureValue.map(_.summary)
            summaries.map(_.lastCommittedIndex).toSet should contain only (expectedIndex)
            summaries.map(_.lastUnappliedIndex).toSet should contain only (expectedIndex)
          //            appliedMap.values.foreach { entries =>
          //              entries.size shouldBe (expectedIndex)
          //              entries.head.index shouldBe expectedIndex
          //              entries.head.term.t shouldBe summaries.head.term
          //            }

        }
      }
    }
    "append multiple entries after a leader switch" ignore {

      ???
    }
  }

}
