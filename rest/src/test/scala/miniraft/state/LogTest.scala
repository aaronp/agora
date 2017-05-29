package miniraft.state

import jabroni.rest.BaseSpec
import org.scalatest.BeforeAndAfterAll

class LogTest
  extends BaseSpec
    with BeforeAndAfterAll {
  "Log(dir)" should {
    "have an initial term of 0 and no entries" in withDir { dir =>
      val log = Log[String](dir)
      log.lastTerm shouldBe Term(0)
      log.latestCommittedEntry shouldBe (empty)
      log.latestUnappliedEntry shouldBe (empty)
    }
    "read and write entries" in withDir { dir =>
      val log = Log[String](dir)
      val appended = log.append(LogEntry(Term(3), 4, "test"))
      appended.lastTerm shouldBe Term(3)
      appended.latestUnappliedEntry shouldBe Option(LogEntry(Term(3), 4, "test"))
      appended.at(4) shouldBe Option(LogEntry(Term(3), 4, "test"))
      appended.latestCommittedEntry shouldBe (empty)

      // now commit
      appended.commit(appended.lastUnappliedIndex)
      appended.latestCommittedEntry shouldBe Option(LogEntry(Term(3), 4, "test"))
    }
  }


}
