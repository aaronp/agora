package riff.raft

import riff.RiffSpec

class RaftNodeTest extends RiffSpec {

  import RaftNodeTest._

  "CandidateNode.onRequestVoteReply" should {
    "become the leader when 1 peer in a cluster of 3 grants the vote" in {
      Given("A cluster of three nodes")
      val candidate = RaftNode("some candidate").
        withPeers(Peer.initial("1"), Peer.initial("2")).onElectionTimeout
      candidate.voteCount() shouldBe 0

      When("It receives a response from peer it knows nothing about")
      val reply = RequestVoteReply("unknown", "some candidate", candidate.currentTerm, true)

      Then("It should not update its role")
//      candidate.onRequestVoteReply(reply) shouldBe candidate
//      candidate.onRequestVoteReply(reply).role shouldBe NodeRole.Follower
//
//      When("It receives a granted response from a known peer")
//      val okReply = RequestVoteReply("2", "some candidate", candidate.currentTerm, true)
//      val leader = candidate.onRequestVoteReply(okReply, 0)
//
//      Then("it should become leader")
//      leader.role shouldBe NodeRole.Leader
      ???
    }
  }
  "FollowerNode" should {
    "grant a vote when the request for vote's term is greater than the follower's term" in {
      val someFollower = RaftNode("some follower")

      val requestFromCandidate = RequestVote.createForPeer("candidate", 2, Peer.initial("some follower"))
      val (newState, reply: RequestVoteReply) = someFollower.onRequestVote(requestFromCandidate)

      newState.votedFor shouldBe Some("candidate")
      newState.currentTerm shouldBe requestFromCandidate.term
      reply.granted shouldBe true
    }
//    "not grant a vote when the request for vote's term is less than the follower's term" in {
//      val someFollower = RaftNode("some follower").updated(3)
//
//      val requestFromCandidate = RequestVote.createForPeer("candidate", 2, Peer.initial("some follower"))
//      val (newState, reply) = someFollower.onRequestVote(requestFromCandidate)
//
//      newState.votedFor shouldBe None
//      newState.currentTerm shouldBe someFollower.currentTerm
//      reply.granted shouldBe false
//    }
    "transition to candidate and produce a request for vote to all nodes when an initial node times out" in {
      Given("A cluster of five nodes")
      val cluster = custerOf(5)

      When("A follower times out")
      val (_, someNode) = cluster.head

      val candidate: CandidateNode = someNode.onElectionTimeout()
      candidate.currentTerm shouldBe someNode.currentTerm + 1
      val messages: List[RequestVote] = RequestVote(candidate).toList

      Then("It should create four request vote messages")
      messages.size shouldBe 4
      messages.map(_.to).size shouldBe 4
      messages.map(_.to) should not contain(someNode.name)

      messages.foreach { rv =>
        rv.from shouldBe someNode.name
        rv.term shouldBe candidate.currentTerm
        rv.lastLogIndex shouldBe 0
        rv.lastLogTerm shouldBe 0
      }
    }
  }
}

object RaftNodeTest {

  def custerOf(nrOfNodes: Int): Map[String, FollowerNode] = {
    val peers: Map[String, Peer] = (1 to nrOfNodes).map { n =>
      val p = Peer.initial(s"node-$n")
      p.name -> p
    }.toMap

    (1 to nrOfNodes).map { n =>
      val name = s"node-$n"
      require(peers.contains(name))
      val node = RaftNode(name).withPeers(peers - name)
      require(node.peersByName.size == nrOfNodes - 1, s"${node.peersByName.size} != $nrOfNodes - 1")
      node.name -> node
    }.toMap.ensuring(_.size == nrOfNodes)
  }
}