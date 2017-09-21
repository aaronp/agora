package agora.exec.workspace

import agora.BaseSpec

class TrackingWorkspaceClientTest extends BaseSpec {

  "TrackingWorkspaceClient" should {
    "only remove files older than the given time" in {
      withDir { dir =>

      }
    }
  }

  "TrackingWorkspaceClient.allFilesAreOlderThanTime" should {
    "return true if all the files in the workspaces are older than the given time" in {
      withDir { dir =>

        val before = agora.api.time.now()
        Thread.sleep(1)
        dir.resolve("a").resolve("b").resolve("file").text = before.toString
        Thread.sleep(1)
        val after = agora.api.time.now()

        TrackingWorkspaceClient.allFilesAreOlderThanTime(dir, before) shouldBe false
        TrackingWorkspaceClient.allFilesAreOlderThanTime(dir, after) shouldBe true

      }
    }
  }

}
