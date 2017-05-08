package miniraft

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

class RaftTest extends WordSpec with Matchers with ScalaFutures {

  "Raft.isMajority" should {
    List(
      (0, 0, false),
      (0, 1, false),
      (1, 1, true),
      (1, 2, false),
      (2, 2, true),
      (1, 3, false),
      (2, 3, true),
      (3, 3, true),
      (1, 4, false),
      (2, 4, false),
      (3, 4, true),
      (4, 4, true),
      (1, 5, false),
      (2, 5, false),
      (3, 5, true),
      (4, 5, true)
    ).foreach {
      case (n, total, expected) =>
        s"return $expected for ($n, $total)" in {
          isMajority(n, total) shouldBe expected
        }
    }
  }
  "Raft" should {
    "initial state" in {
      val cluster = new Cluster()
      val expectedView = Map[NodeId, PeerState](
        "A" -> PeerState.initial,
        "B" -> PeerState.initial,
        "C" -> PeerState.initial,
        "D" -> PeerState.initial,
        "E" -> PeerState.initial
      )
      cluster.nodes.foreach { node =>
        node.clusterView shouldBe expectedView
        node.votedFor shouldBe None
        node.currentTerm.value shouldBe 1
        node.commitIndex shouldBe 0
        node.isFollower shouldBe true
      }
    }
    "initial request vote" in {
//      val cluster = new Cluster()
//      val voteBus = Broadcast.Record(cluster.EventBus)
//      val replyBus = Broadcast.Record(cluster.EventBus)
//      cluster.triggerHeartbeatTimeout("A", voteBus, replyBus)
//      voteBus.pendingRequests.size shouldBe cluster.size
//      voteBus.pendingRequests.foreach {
//        case (vote: RequestVote, _) =>
//          vote.term.value shouldBe 2
//          vote.lastLogIndex shouldBe 0
//          vote.lastLogTerm.value shouldBe 0
//      }
//      voteBus.flushAll.map(_.futureValue).foreach {
//        case vote: RequestVoteResponse =>
//          vote.granted shouldBe true
//          vote.term.value shouldBe 2
//      }
//      replyBus.pendingRequests.foreach {
//        case (heartbeat: AppendEntries[_], _) =>
//          heartbeat.entries shouldBe (empty)
//          heartbeat.term.value shouldBe 2
//          heartbeat.prevIndex shouldBe 0
//          heartbeat.prevTerm.value shouldBe 0
//      }
//      val fa = replyBus.flushAll
//      fa.map(_.futureValue).foreach {
//        case reply: AppendEntriesResponse =>
//          reply.term.value shouldBe 2
//          reply.success shouldBe true
//          reply.matchIndex shouldBe 1
//      }
//      cluster("A").isLeader shouldBe true
//      cluster.nodeIds.foreach { id =>
//        cluster(id).isLeader shouldBe (id == "A")
//        cluster(id).isFollower shouldBe (id != "A")
//        cluster(id).isCandidate shouldBe false
//      }
    }
  }
}