package riff.impl

import riff.RiffSpec
import riff.raft.RaftLog.LogWritten
import riff.raft.{LogCoords, LogState, RaftLog}


class RaftLogTest extends RiffSpec {

  "RaftLog.ForDir append" should {
    "increment the index on each append" in {
      withDir { dir =>
        val log: RaftLog.ForDir[String] = RaftLog[String](dir)
        log.logState shouldBe LogState.Empty

        log.append(LogCoords(2, 1), "first entry") shouldBe LogWritten(log.dir.resolve("1.entry"))
        log.logState shouldBe LogState(commitIndex = 0, latestTerm = 2, latestIndex = 1)

        log.append(LogCoords(2, 2), "first entry") shouldBe LogWritten(log.dir.resolve("2.entry"))
        log.logState shouldBe LogState(commitIndex = 0, latestTerm = 2, latestIndex = 2)

        log.append(LogCoords(3, 3), "first entry") shouldBe LogWritten(log.dir.resolve("3.entry"))
        log.logState shouldBe LogState(commitIndex = 0, latestTerm = 3, latestIndex = 3)

        log.append(LogCoords(3, 4), "first entry") shouldBe LogWritten(log.dir.resolve("4.entry"))
        log.logState shouldBe LogState(commitIndex = 0, latestTerm = 3, latestIndex = 4)
      }
    }
  }
}
