package agora.exec.workspace

import java.nio.file.Path
import java.util.UUID

import agora.BaseSpec
import agora.exec.model.Upload
import agora.rest.HasMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.{Await, Future}

class WorkspaceClientTest extends BaseSpec with HasMaterializer {

  "WorkspaceClient.list" should {
    "not list closed workspaces" in {
      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client
          .upload("foo", "bar", Source.single(ByteString("hi")))
          .futureValue
          .exists() shouldBe true

        client.list().futureValue should contain only ("foo")

        client.close("foo").futureValue shouldBe true

        client.list().futureValue shouldBe empty
      }
    }
    "list all known workspaces createdAfter the specified time" in {

      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client.list().futureValue shouldBe empty

        val before = agora.api.time.now()
        client.upload("x", Upload.forText("file", "content")).futureValue.exists() shouldBe true
        client.upload("y", Upload.forText("file", "content")).futureValue.exists() shouldBe true

        client.list(createdAfter = Option(before.minusSeconds(2))).futureValue should contain only ("x", "y")
        client.list(createdAfter = Option(before.plusSeconds(2))).futureValue shouldBe empty
      }
    }
    "list all known workspaces createdBefore the specified time" in {

      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client.list().futureValue shouldBe empty

        val before = agora.api.time.now()
        client.upload("x", Upload.forText("file", "content")).futureValue.exists() shouldBe true
        client.upload("y", Upload.forText("file", "content")).futureValue.exists() shouldBe true

        client.list(createdBefore = Option(before)).futureValue shouldBe empty
        client.list(createdBefore = Option(before.plusSeconds(2))).futureValue should contain only ("x", "y")
      }
    }

    "list all known workspaces between createdAfter and createdBefore " in {

      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        client.list().futureValue shouldBe empty

        val before = agora.api.time.now()
        client.upload("x", Upload.forText("file", "content")).futureValue.exists() shouldBe true

        client
          .list(createdAfter = Option(before.minusSeconds(2)), createdBefore = Option(before.plusSeconds(2)))
          .futureValue should contain only ("x")

        client
          .list(createdAfter = Option(before.plusSeconds(2)), createdBefore = Option(before.plusSeconds(3)))
          .futureValue shouldBe empty
      }
    }
  }
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
        val workspace = UUID.randomUUID().toString
        val client    = WorkspaceClient(containerDir, system)
        val started   = agora.api.time.now()
        val awaitFuture: Future[Path] =
          client.await(workspace, Set("some.file"), testTimeout.toMillis)

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
    "not close workspaces when ifNotModifiedSince is specified and the workspaces has been modified since" in {
      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)
        val before = agora.api.time.now().minusSeconds(1)
        client.upload("hi", Upload.forText("x", "y")).futureValue
        client.upload("hi", Upload.forText("second", "one")).futureValue

        client.close("hi", ifNotModifiedSince = Option(before)).futureValue shouldBe false
        client.list().futureValue should contain only ("hi")

        val closed = client.close("hi", ifNotModifiedSince = Option(before.plusSeconds(10))).futureValue
        closed shouldBe true
        client.list().futureValue shouldBe empty
      }
    }
    "not close workspaces when failPendingDependencies is false and there are pending dependencies" in {
      withDir { containerDir =>
        val client = WorkspaceClient(containerDir, system)

        // we don't get an ack when an 'await' is received, so in this test we send one expectation which
        // will never get fulfilled, followed by another which does. That way, but 'flushing' the subsequent
        // await call, we have a better certainty that the first has been received
        val neverGonnaFuture = client.await("ws1", Set("never.gonna.appear"), testTimeout.toMillis)
        val fileOne          = client.await("ws1", Set("file.one"), testTimeout.toMillis)
        client.upload("ws1", Upload.forText("file.one", "y")).futureValue.exists() shouldBe true
        fileOne.futureValue.exists() shouldBe true
        neverGonnaFuture.isCompleted shouldBe false

        // call the method under test
        withClue("we shouldn't be able to close a workspace while there are still things pending") {
          client.close("ws1", failPendingDependencies = false).futureValue shouldBe false
          client.list().futureValue should contain only ("ws1")
        }

        // verify we still are awaiting that dependency
        withClue("the 'await' which never is satisfied should still be pending after the failed close call") {
          neverGonnaFuture.isCompleted shouldBe false
        }

        // now allow it to kill the dependency
        withClue("we should be able to close a workspace with things pending when failPendingDependencies is set") {
          client.close("ws1", failPendingDependencies = true).futureValue shouldBe true
        }
        client.list().futureValue shouldBe empty

        val exp = intercept[Exception] {
          neverGonnaFuture.block
        }
        exp.getMessage should include("Workspace 'ws1' has been closed")
      }
    }
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
        client.upload("meh", Upload.forText("file", "content")).futureValue.exists() shouldBe true
        containerDir.children.size shouldBe 1

        client.upload("foo", Upload.forText("file", "content")).futureValue.exists() shouldBe true
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
        client
          .upload("some id", Upload.forText("i.wasUploaded", "123"))
          .futureValue
          .exists() shouldBe true
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
        client
          .upload("some id", Upload.forText("file.one", "first file"))
          .futureValue
          .exists() shouldBe true
        dirFuture.isCompleted shouldBe false

        // upload a second, but with a different name
        client
          .upload("some id", Upload.forText("file.three", "another file"))
          .futureValue
          .exists() shouldBe true
        dirFuture.isCompleted shouldBe false

        // upload file.two, but to a different session
        client
          .upload("wrong session", Upload.forText("file.two", "different session"))
          .futureValue
          .exists() shouldBe true
        dirFuture.isCompleted shouldBe false

        // finally upload the file we expect in our 'some id' session.
        client
          .upload("some id", Upload.forText("file.two", "finally ready!"))
          .futureValue
          .exists() shouldBe true
        val sessionDir = dirFuture.futureValue
        sessionDir.children.map(_.fileName) should contain only ("file.one", "file.two", "file.three")

        sessionDir.resolve("file.two").text shouldBe "finally ready!"

        // the 'some id' and 'wrong session' sessions
        containerDir.children.size shouldBe 2
      }

    }
  }
}
