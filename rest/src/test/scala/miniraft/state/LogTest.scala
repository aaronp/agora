package miniraft.state

import agora.rest.BaseSpec
import org.scalatest.BeforeAndAfterAll

class LogTest extends BaseSpec with BeforeAndAfterAll {
  "Log(dir)" should {
    "have an initial term of 0 and no entries" in withDir { dir =>
      val log = Log[String](dir)(_ => ???)
      log.lastTerm shouldBe Term(0)
      log.latestCommittedEntry shouldBe (empty)
      log.latestUnappliedEntry shouldBe (empty)
    }
    "read and write entries" in withDir { dir =>
      var applied  = List[LogEntry[String]]()
      val log      = Log[String](dir)(e => applied = e :: applied)
      val first    = LogEntry(Term(3), 4, "test")
      val appended = log.append(first)
      applied should be(empty)
      appended.lastTerm shouldBe Term(3)
      appended.latestUnappliedEntry shouldBe Option(first)
      appended.at(4) shouldBe Option(LogEntry(Term(3), 4, "test"))
      appended.latestCommittedEntry shouldBe (empty)

      // now commit
      appended.commit(appended.lastUnappliedIndex)
      applied should contain only (first)
      appended.latestCommittedEntry shouldBe Option(first)
    }
  }

}
