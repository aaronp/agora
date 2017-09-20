package agora.exec.workspace

import java.nio.file.Path
import java.util.UUID

import agora.BaseSpec
import agora.exec.model.Upload
import agora.rest.HasMaterializer

import scala.concurrent.{Await, Future}

class WorkspaceClientTest extends BaseSpec with HasMaterializer {
  "WorkspaceClient.upload" should {

    "not create directories unless files have been actually uploaded" in {
      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client.await("mustBeEmpty", Set("meh"), testTimeout.toMillis)
        containerDir.children.size shouldBe 0
        client.close("mustBeEmpty").futureValue shouldBe false
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
  "WorkspaceClient.triggerUploadCheck" should {
    "reevaluate a workspace" in {
      withDir { containerDir =>
        val workspace                 = UUID.randomUUID().toString
        val client                    = WorkspaceClient(containerDir, system)
        val started                   = agora.api.time.now()
        val awaitFuture: Future[Path] = client.await(workspace, Set("some.file"), testTimeout.toMillis)

        // give the workspace client some time to potentially find the non-existent file
        // this is always tough to do ... how long do you wait for something not to happen,
        // but not have the test take ages?
        Thread.sleep(300)

        val done = awaitFuture.isCompleted
        val errorMessage = if (done) {
          val path      = awaitFuture.futureValue
          val errorTime = agora.api.time.now()

          s""" Awaiting on $workspace under $containerDir has already completed
             | We stated at
             | $started
             | w/ test timeout $testTimeout
             | and it is now
             | $errorTime
             |
                 | The path is $path
             | which contains:
             | ${path.nestedFiles().mkString("\n")}
             |
                 | and the test directory contents are:
                 |
                 | ${containerDir.nestedFiles().mkString("\n")}
             |
               """.stripMargin
        } else {
          "We should not yet have been notified of the file"
        }

        withClue(errorMessage) {
          done shouldBe false
        }

        // manually create some.file (e.g. no via workspaces.upload)
        //
        // our 'await' future shouldn't be completed yet though (as it may
        // be using a file watcher)... we have to manually kick it via
        // triggerUploadCheck
        val expectedFile = containerDir.resolve(workspace).resolve("some.file").text = "hi"

        // call the method under test -- await should now complete
        client.triggerUploadCheck(workspace)

        awaitFuture.futureValue shouldBe expectedFile.parent.get
      }
    }
  }
  "WorkspaceClient.close" should {
    "fail any pending await calls" in {
      withDir { containerDir =>
        val client      = WorkspaceClient(containerDir, system)
        val awaitFuture = client.await("some id", Set("won't ever arrive"), testTimeout.toMillis)
        client.close("some id").futureValue shouldBe false

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
    "return in error when not all uploads appear within a timeout" in {
      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client.upload("some id", Upload.forText("i.wasUploaded", "123")).futureValue shouldBe true
        val awaitFuture = client.await("some id", Set("i.wasUploaded", "i.wasnt"), 1)
        val err = intercept[Exception] {
          Await.result(awaitFuture, testTimeout)
        }

        containerDir.children.foreach(println)

        err.getMessage shouldBe "Still waiting for 1 files [i.wasnt] in workspace 'some id' after 1 millisecond"
      }
    }
    "block until files are all available in a workspace" in {
      withDir { containerDir =>
        val client    = WorkspaceClient(containerDir, system)
        val dirFuture = client.await("some id", Set("file.one", "file.two"), testTimeout.toMillis)
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
