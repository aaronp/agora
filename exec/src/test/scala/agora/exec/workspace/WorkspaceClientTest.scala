package agora.exec.workspace

import agora.exec.model.Upload
import agora.rest.{BaseSpec, HasMaterializer}

import scala.concurrent.Await

class WorkspaceClientTest extends BaseSpec with HasMaterializer {
  "WorkspaceClient.upload" should {

    "not create directories unless files have been actually uploaded" in {
      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client.await("mustBeEmpty", Set("meh"))
        containerDir.children.size shouldBe 0
        client.close("mustBeEmpty").futureValue shouldBe true
        containerDir.children.size shouldBe 0
      }
    }
    "not create directories when close is called for an unknown id" in {
      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client.close("unknown").futureValue shouldBe false
        containerDir.children.size shouldBe 0
      }
    }
  }
  "WorkspaceClient.close" should {
    "fail any pending await calls" in {
      withDir { containerDir =>
        val client      = WorkspaceClient(containerDir, system)
        val awaitFuture = client.await("some id", Set("won't ever arrive"))
        client.close("some id").futureValue shouldBe true

        val err = intercept[IllegalStateException] {
          Await.result(awaitFuture, testTimeout)
        }

        err.getMessage shouldBe "Workspace 'some id' has been closed"
      }
    }
    "delete the files in a workspace" in {
      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client.upload("meh", Upload.forText("file", "content")).futureValue shouldBe true
        containerDir.children.size shouldBe 1

        client.upload("foo", Upload.forText("file", "content")).futureValue shouldBe true
        containerDir.children.size shouldBe 2

        // call the method under test
        client.close("foo").futureValue shouldBe true

        containerDir.children.size shouldBe 1

        // closing again shouldn't do owt
        client.close("foo").futureValue shouldBe false
      }
    }
  }
  "WorkspaceClient.await" should {
    "block until files are all available in a workspace" in {
      withDir { containerDir =>
        val client    = WorkspaceClient(containerDir, system)
        val dirFuture = client.await("some id", Set("file.one", "file.two"))
        dirFuture.isCompleted shouldBe false

        // upload one of the files
        client.upload("some id", Upload.forText("file.one", "first file")).futureValue shouldBe true
        dirFuture.isCompleted shouldBe false

        // upload a second, but with a different name
        client.upload("some id", Upload.forText("file.three", "another file")).futureValue shouldBe true
        dirFuture.isCompleted shouldBe false

        // upload file.two, but to a different session
        client.upload("wrong session", Upload.forText("file.two", "different session")).futureValue shouldBe true
        dirFuture.isCompleted shouldBe false

        // finally upload the file we expect in our 'some id' session.
        client.upload("some id", Upload.forText("file.two", "finally ready!")).futureValue shouldBe true
        val sessionDir = dirFuture.futureValue
        sessionDir.children.map(_.fileName) should contain only ("file.one", "file.two", "file.three")

        sessionDir.resolve("file.two").text shouldBe "finally ready!"

        // the 'some id' and 'wrong session' sessions
        containerDir.children.size shouldBe 2
      }

    }
  }
}
