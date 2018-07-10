package riff

class AppendEntriesTest extends RiffSpec {
  "AppendEntries" should {
    "Send a no-op when a candidate becomes a leader" in {

      val leader = LeaderNode("boss", NodeData().inc.inc.withPeers(Peer("foo"), Peer("bar")))
      val request = AppendEntries.forLeader(leader, "")

    }
  }

}
