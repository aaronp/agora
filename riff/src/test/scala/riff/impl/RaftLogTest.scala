package riff.impl

import riff.RiffSpec
import riff.raft.RaftLog.LogAppendResult
import riff.raft.{LogCoords, LogState, RaftLog}

class RaftLogTest extends RiffSpec {

  "RaftLog.ForDir append" should {
    "remove old appended entries if asked to append an earlier entry with a greater term" in {
      withLog { log =>
        log.logState shouldBe LogState.Empty

        log.append(LogCoords(4, 1), "a") shouldBe LogAppendResult(log.dir.resolve("1.entry"))
        log.append(LogCoords(4, 2), "b") shouldBe LogAppendResult(log.dir.resolve("2.entry"))
        log.append(LogCoords(4, 3), "c") shouldBe LogAppendResult(log.dir.resolve("3.entry"))

        log.termForIndex(1) shouldBe Some(4)
        log.termForIndex(2) shouldBe Some(4)
        log.termForIndex(3) shouldBe Some(4)
        log.dir.children should contain allElementsOf ((1 to 3).map(i => log.dir.resolve(s"$i.entry")))
        log.latestAppended() shouldBe LogCoords(4, 3)

        // call the method under test -- appending term 5 at index 2 should remove our previous entries
        log.append(LogCoords(5, 2), "replacing entry") shouldBe LogAppendResult(
          written = log.dir.resolve("2.entry"),
          replaced = Seq(
            log.dir.resolve("2.entry"),
            log.dir.resolve("3.entry")
          )
        )
        log.latestAppended() shouldBe LogCoords(5, 2)
        log.termForIndex(1) shouldBe Some(4)
        log.termForIndex(2) shouldBe Some(5)
        log.termForIndex(3) shouldBe None
      }
    }
    "error if asked to skip a log entry" in {
      withLog { log =>
        log.logState shouldBe LogState.Empty

        log.append(LogCoords(7, 1), "first") shouldBe LogAppendResult(log.dir.resolve("1.entry"))
        val exp = intercept[Exception] {
          log.append(LogCoords(7, 3), "bang")
        }
        exp.getMessage should include("Attempt to skip a log entry by appending LogCoords(7,3) when the latest entry was LogCoords(7,1)")
      }
    }
    "error if asked to append the same entry with the same term" in {
      withLog { log =>
        log.logState shouldBe LogState.Empty

        log.append(LogCoords(7, 1), "first") shouldBe LogAppendResult(log.dir.resolve("1.entry"))
        val exp = intercept[Exception] {
          log.append(LogCoords(7, 1), "bang")
        }
        exp.getMessage should include("Attempt to append LogCoords(7,1) when our latest term is LogCoords(7,1)")
      }
    }

    "error if asked to append the same entry with an earlier term" in {
      withLog { log =>
        log.logState shouldBe LogState.Empty

        log.append(LogCoords(7, 1), "first") shouldBe LogAppendResult(log.dir.resolve("1.entry"))
        val exp = intercept[IllegalArgumentException] {
          log.append(LogCoords(6, 1), "bang")
        }
        exp.getMessage should include("Attempt to append LogCoords(6,1) when our latest term is LogCoords(7,1)")

      }
    }
    "increment the index on each append" in {
      withLog { log =>
        log.logState shouldBe LogState.Empty

        log.termForIndex(0) shouldBe None
        log.termForIndex(1) shouldBe None
        log.logState shouldBe LogState.Empty

        log.append(LogCoords(2, 1), "first entry") shouldBe LogAppendResult(log.dir.resolve("1.entry"))
        log.logState shouldBe LogState(commitIndex = 0, latestTerm = 2, latestIndex = 1)
        log.termForIndex(1) shouldBe Some(2)

        log.append(LogCoords(2, 2), "first entry") shouldBe LogAppendResult(log.dir.resolve("2.entry"))
        log.logState shouldBe LogState(commitIndex = 0, latestTerm = 2, latestIndex = 2)
        log.termForIndex(2) shouldBe Some(2)

        log.append(LogCoords(3, 3), "first entry") shouldBe LogAppendResult(log.dir.resolve("3.entry"))
        log.logState shouldBe LogState(commitIndex = 0, latestTerm = 3, latestIndex = 3)
        log.termForIndex(2) shouldBe Some(2)
        log.termForIndex(3) shouldBe Some(3)

        log.append(LogCoords(3, 4), "first entry") shouldBe LogAppendResult(log.dir.resolve("4.entry"))
        log.logState shouldBe LogState(commitIndex = 0, latestTerm = 3, latestIndex = 4)
        log.termForIndex(4) shouldBe Some(3)
      }
    }
  }

  def withLog(test: RaftLog.ForDir[String] => Unit) = withDir { dir => test(RaftLog[String](dir))
  }

}
