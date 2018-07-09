package riff

import org.scalatest.{GivenWhenThen, Matchers, WordSpec}

class RaftNodeTest extends WordSpec with Matchers with GivenWhenThen {

  import RaftNodeTest._

  "CandidateNode" should {
    "" in {
      Given("A cluster of five nodes")


    }
  }
  "FollowerNode" should {
    "grant a vote when the request for vote's term is greater than the follower's term" in {
      val someFollower = RaftNode.initial("some follower").withPeers(Map(
        "candidate" -> Peer.initial("candidate"),
        "meh" -> Peer.initial("meh")
      ))

      val requestFromCandidate = RequestVote.createForPeer("candidate", 2, Peer.initial("some follower"))
      val (newState, reply) = someFollower.onRequestVote(requestFromCandidate, now = 1234L)

      newState.votedFor shouldBe Some("candidate")
      newState.currentTerm shouldBe requestFromCandidate.term
      reply.granted shouldBe true
    }
    "not grant a vote when the request for vote's term is less than the follower's term" in {
      val someFollower = RaftNode.initial("some follower").withPeers(Map(
        "candidate" -> Peer.initial("candidate"),
        "meh" -> Peer.initial("meh")
      )).withTerm(3)

      val requestFromCandidate = RequestVote.createForPeer("candidate", 2, Peer.initial("some follower"))
      val (newState, reply) = someFollower.onRequestVote(requestFromCandidate, now = 1234L)

      newState.votedFor shouldBe None
      newState.currentTerm shouldBe someFollower.currentTerm
      reply.granted shouldBe false
    }
    "transition to candidate and produce a request for vote to all nodes when an initial node times out" in {
      Given("A cluster of five nodes")
      val cluster = custerOf(5)

      When("A follower times out")
      val (_, someNode) = cluster.head

      val candidate: CandidateNode = someNode.onElectionTimeout(now = 1234)
      candidate.currentTerm shouldBe someNode.currentTerm + 1
      candidate.votedFor shouldBe candidate.name
      val messages: List[RequestVote] = RaftMessage.requestVote(candidate).toList

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
      val node = RaftNode.initial(name).withPeers(peers - name)
      require(node.peersByName.size == nrOfNodes - 1, s"${node.peersByName.size} != $nrOfNodes - 1")
      node.name -> node
    }.toMap.ensuring(_.size == nrOfNodes)
  }
}