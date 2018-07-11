package riff

import riff.raft.{AppendEntries, FollowerNode, NodeData}

class FollowerNodeTest extends RiffSpec {
  "FollowerNode.onAppendEntries" should {
      val request = AppendEntries[List[String]]("leader", "dave", term = 7, prevIndex = 10, prevTerm = 6, Nil, commitIndex = 10)


    "fail but update the nodes term and 'votedFor' if it receives an AppendEntries request for a later term" in {
      val follower = FollowerNode("dave", NodeData(), None)
      val (newState, resp) = follower.onAppendEntries(request, 1234)

      newState.currentTerm shouldBe 7
      newState.votedFor shouldBe None
      resp.success shouldBe false
    }

    "succeed for requests matching the same term and commit index" in {
      val follower = FollowerNode("dave", NodeData(), None).updated(term =7)
      val (newState, resp) = follower.onAppendEntries(request, 1234)

      newState.currentTerm shouldBe 7
      newState.votedFor shouldBe None
      resp.success shouldBe true
    }
  }
}
