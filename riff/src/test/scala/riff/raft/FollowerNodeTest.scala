package riff.raft

class FollowerNodeTest extends RiffSpec {
  "FollowerNode.onAppendEntries" should {
      val request = AppendEntries[List[String]]("leader", "dave", term = 7, prevIndex = 0, prevTerm = 6, Nil, commitIndex = 0)


    "fail but update the nodes term and 'votedFor' if it receives an AppendEntries request for a later term" in {
      val follower = FollowerNode("dave", NodeData(), None)
      val (newState, resp) = follower.onAppendEntries(request, 1234)

      newState.currentTerm shouldBe 7
      resp.matchIndex shouldBe 1
      newState.votedFor shouldBe None
      resp.success shouldBe false
    }

    "succeed for requests matching the same term and commit index" in {
      val follower = FollowerNode("dave", NodeData(), None).updated(term =7)
      val (newState, resp) = follower.onAppendEntries(request, 1234)

      newState.currentTerm shouldBe 7
      newState.votedFor shouldBe None
      resp.success shouldBe true
      resp.matchIndex shouldBe 1
    }
  }
}
