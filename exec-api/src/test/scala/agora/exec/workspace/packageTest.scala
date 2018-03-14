package agora.exec.workspace

import agora.BaseExecApiSpec

class packageTest extends BaseExecApiSpec {

  "allFilesAreOlderThanTime" should {
    "return true if all the files in the workspaces are older than the given time" in {
      withDir { dir =>
        val before = agora.time.now()
        dir.resolve("a").resolve("b").resolve("file").text = before.toString

        allFilesAreOlderThanTime(dir, before.plusSeconds(2)) shouldBe true
        allFilesAreOlderThanTime(dir, before.minusSeconds(2)) shouldBe false
      }
    }
  }

}
