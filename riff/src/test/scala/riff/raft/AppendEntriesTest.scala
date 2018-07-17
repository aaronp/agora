package riff.raft

import riff.RiffSpec

import scala.collection.immutable

class AppendEntriesTest extends RiffSpec {
  "AppendEntries" should {
    "Send a no-op when a candidate becomes a leader" in {

      val leader = LeaderNode(NodeData("boss").incTerm.withPeers(Peer("foo"), Peer("bar")))
      val requests: immutable.Iterable[AppendEntries[String]] = AppendEntries.forLeader(leader, "")
      requests.size shouldBe 2
      requests.foreach(_.commitIndex shouldBe 0)
      requests.foreach(_.from shouldBe "boss")
      requests.map(_.to) should contain only ("foo", "bar")

    }
  }
}
