package agora.exec.workspace

import agora.exec.model.Upload
import agora.rest.{BaseSpec, HasMaterializer}

class WorkspaceClientTest extends BaseSpec with HasMaterializer {
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
        sessionDir.children.map(_.fileName) should contain only ("file.one", "file.two")

        sessionDir.resolve("file.two").text shouldBe "finally ready!"

        // the 'some id' and 'wrong session' sessions
        containerDir.children.size shouldBe 2
      }

    }
  }
}
