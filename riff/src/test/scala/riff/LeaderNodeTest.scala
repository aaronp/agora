package riff

import riff.raft.{AppendEntries, AppendEntriesReply, LeaderNode}

class LeaderNodeTest extends RiffSpec {


  "LeaderNode" should {
    "send append entries" in {
      val leader =
        leader"""
           >        name : leader
           >        role : Leader
           >current term : 2
           >   voted for : leader
           >commit index : 0
           >               Peer   | Next Index | Match Index | Vote Granted
           >               second | 1          | 0           | false
           >               third  | 1          | 0           | false
           >"""


      val requests: List[AppendEntries[String]] = AppendEntries.forLeader(leader, "").toList
      requests.size shouldBe 2
      requests.foreach { ae =>
        ae.term shouldBe 2
        ae.prevIndex shouldBe 0
        ae.commitIndex shouldBe 0
      }

      val newLeader: LeaderNode = leader.onAppendEntriesReply(AppendEntriesReply("second", "leader", 0, term = 2, success = true, matchIndex = 0))

      newLeader shouldBe leader"""
           >        name : leader
           >        role : Leader
           >current term : 2
           >   voted for : leader
           >commit index : 0
           >               Peer   | Next Index | Match Index | Vote Granted
           >               second | 2          | 1           | false
           >               third  | 1          | 0           | false
           >"""
    }
  }

  // a test for our test parsing/assertion is working properly
  "leaderInState" should {
    "be able to parse and assert w/ 'shouldMatch' leader nodes" in {
      val leaderText =
        """name : leader
           >        role : Leader
           >current term : 2
           >   voted for : leader
           >commit index : 0
           >               Peer   | Next Index | Match Index | Vote Granted
           >               fifth  | 1          | 0           | false
           >               fourth | 1          | 0           | false
           >               second | 1          | 0           | false
           >               third  | 1          | 0           | false
           >""".stripMargin('>')

      val actual = leaderInState(leaderText)
      actual.peersByName.keySet shouldBe Set("second","third","fourth","fifth")
      actual shouldMatch leaderText
    }
  }
}
