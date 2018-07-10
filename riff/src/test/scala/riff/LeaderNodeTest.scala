package riff

class LeaderNodeTest extends RiffSpec {


  "LeaderNode" should {
    "" in {
      val leader =
        leader"""
           >        name : leader
           >       state : Leader
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
        ae
      }
    }
  }

  // a test for our test parsing/assertion is working properly
  "leaderInState" should {
    "be able to parse and assert w/ 'shouldMatch' leader nodes" in {
      val leaderText =
        """name : leader
           >       state : Leader
           >current term : 2
           >   voted for : leader
           >commit index : 0
           >               Peer   | Next Index | Match Index | Vote Granted
           >               second | 1          | 0           | false
           >               third  | 1          | 0           | false
           >               fourth | 1          | 0           | false
           >               fifth  | 1          | 0           | false
           >""".stripMargin('>')

      leaderInState(leaderText) shouldMatch leaderText
    }
  }
}
