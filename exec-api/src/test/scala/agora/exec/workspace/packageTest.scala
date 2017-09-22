package agora.exec.workspace

import agora.BaseSpec

class packageTest extends BaseSpec {


  "allFilesAreOlderThanTime" should {
    "return true if all the files in the workspaces are older than the given time" in {
      withDir { dir =>
        val before = agora.api.time.now()
        dir.resolve("a").resolve("b").resolve("file").text = before.toString

        allFilesAreOlderThanTime(dir, before.plusSeconds(2)) shouldBe true
        allFilesAreOlderThanTime(dir, before.minusSeconds(2)) shouldBe false
      }
    }
  }

}
