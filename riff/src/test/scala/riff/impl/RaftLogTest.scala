package riff.impl

import riff.RiffSpec
import riff.raft.RaftLog.LogWritten
import riff.raft.{LogCoords, RaftLog}

class RaftLogTest extends RiffSpec {

  "RaftLog.append" should {
    "increment the index on each append" in {
      withDir { dir =>
        import agora.io.implicits._
        val log: RaftLog.ForDir[String] = RaftLog[String](dir)
        log.dir.children.isEmpty shouldBe true

        log.append(LogCoords(2, 1), "first entry") shouldBe LogWritten(log.dir.resolve("1.entry"))

      }
    }
  }
}
