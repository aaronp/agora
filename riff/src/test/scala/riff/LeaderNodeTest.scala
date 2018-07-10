package riff

class LeaderNodeTest extends RiffSpec {

  "LeaderNode" should {
    "be created w/ a peer view of the cluster based on the RequestVote responses" in {
      val leader = LeaderNode("leader", NodeData()).updated(term = 2).withPeers(Peer("second"), Peer("third"))

      println()

    }
  }
}
