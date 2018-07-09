package riff

import org.scalatest.{GivenWhenThen, Matchers, WordSpec}

class RaftNodeTest extends WordSpec with Matchers with GivenWhenThen {

  import RaftNodeTest._

  "RaftNode" should {
    "Send a request for vote to all nodes" in {
      Given("A cluster of five nodes")
      val cluster = custerOf(5)

      When("A follower times out")
      val (_, someNode) = cluster.head

      val candidate: CandidateNode = someNode.onElectionTimeout(now = 1234)
      candidate.currentTerm shouldBe someNode.currentTerm + 1
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