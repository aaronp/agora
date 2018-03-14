package agora.exec.workspace

import java.nio.file.Path
import java.util.UUID

import agora.BaseExecApiSpec
import agora.exec.model.Upload
import agora.rest.HasMaterializer
import akka.actor.PoisonPill
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait WorkspaceClientTCK extends BaseExecApiSpec with HasMaterializer {

  def withWorkspaceClient[T](testWithClient: (WorkspaceClient, Path) => T): T
//  = {
//
//    withDir { containerDir =>
//      val client = WorkspaceClient(containerDir, system, 100.millis)
//      val result = testWithClient(client, containerDir)
//      client.endpointActor ! PoisonPill
//      result
//    }
//  }

  "WorkspaceClient.list" should {
    "not list closed workspaces" in {
      withWorkspaceClient { (client, _) =>
        val (_, path) = client
          .upload("foo", "bar", Source.single(ByteString("hi")))
          .futureValue

        path.exists() shouldBe true

        client.list().futureValue should contain only ("foo")

        client.close("foo").futureValue shouldBe true

        client.list().futureValue shouldBe empty
      }
    }
    "list all known workspaces createdAfter the specified time" in {
      withWorkspaceClient { (client, _) =>
        client.list().futureValue shouldBe empty

        val before = agora.time.now()
        client.upload("x", Upload.forText("file", "content")).futureValue._2.exists() shouldBe true
        client.upload("y", Upload.forText("file", "content")).futureValue._2.exists() shouldBe true

        client.list(createdAfter = Option(before.minusSeconds(2))).futureValue should contain only ("x", "y")
        client.list(createdAfter = Option(before.plusSeconds(2))).futureValue shouldBe empty
      }
    }
    "list all known workspaces createdBefore the specified time" in {
      withWorkspaceClient { (client, _) =>
        client.list().futureValue shouldBe empty

        val before = agora.time.now()
        client.upload("x", Upload.forText("file", "content")).futureValue._2.exists() shouldBe true
        client.upload("y", Upload.forText("file", "content")).futureValue._2.exists() shouldBe true

        client.list(createdBefore = Option(before)).futureValue shouldBe empty
        client.list(createdBefore = Option(before.plusSeconds(2))).futureValue should contain only ("x", "y")
      }
    }

    "list all known workspaces between createdAfter and createdBefore " in {
      withWorkspaceClient { (client, containerDir) =>
        client.list().futureValue shouldBe empty

        val before = agora.time.now()
        client.upload("x", Upload.forText("file", "content")).futureValue._2.exists() shouldBe true

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
      withWorkspaceClient { (client, containerDir) =>
        client.awaitWorkspace("mustBeEmpty", Set("meh"), testTimeout.toMillis)
        containerDir.children.size shouldBe 0
        client.close("mustBeEmpty").futureValue shouldBe false
        containerDir.children.size shouldBe 0
      }
    }
    "not create directories when close is called for an unknown id" in {
      withWorkspaceClient { (client, containerDir) =>
        client.close("unknown").futureValue shouldBe false
        containerDir.children.size shouldBe 0
      }
    }
  }
  "WorkspaceClient.markComplete" should {
    "reevaluate a workspace" in {
      withWorkspaceClient { (client, containerDir) =>
        val workspace = UUID.randomUUID().toString
        val started   = agora.time.now()
        val awaitFuture: Future[Path] =
          client.awaitWorkspace(workspace, Set("some.file"), testTimeout.toMillis)

        // give the workspace client some time to potentially find the non-existent file
        // this is always tough to do ... how long do you wait for something not to happen,
        // but not have the test take ages?
        Thread.sleep(testNegativeTimeout.toMillis)

        val done = awaitFuture.isCompleted
        val errorMessage = if (done) {
          val path      = awaitFuture.futureValue
          val errorTime = agora.time.now()

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
        // our 'awaitWorkspace' future shouldn't be completed yet though (as it may
        // be using a file watcher)... we have to manually kick it via
        // triggerUploadCheck
        val expectedFile = containerDir.resolve(workspace).resolve("some.file").text = "hi"

        // call the method under test -- awaitWorkspace should now complete
        client.markComplete(workspace, Map("some.file" -> "hi".getBytes.size))

        awaitFuture.futureValue shouldBe expectedFile.parent.get
      }
    }
  }
  "WorkspaceClient.close" should {
    "not close workspaces when ifNotModifiedSince is specified and the workspaces has been modified since" in {
      withWorkspaceClient { (client, _) =>
        val before = agora.time.now().minusSeconds(1)
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
      withWorkspaceClient { (client, _) =>
        // we don't get an ack when an 'awaitWorkspace' is received, so in this test we send one expectation which
        // will never get fulfilled, followed by another which does. That way, but 'flushing' the subsequent
        // awaitWorkspace call, we have a better certainty that the first has been received
        val neverGonnaFuture = client.awaitWorkspace("ws1", Set("never.gonna.appear"), testTimeout.toMillis)
        val fileOne          = client.awaitWorkspace("ws1", Set("file.one"), testTimeout.toMillis)
        client.upload("ws1", Upload.forText("file.one", "y")).futureValue._2.exists() shouldBe true
        fileOne.futureValue.exists() shouldBe true
        neverGonnaFuture.isCompleted shouldBe false

        // call the method under test
        withClue("we shouldn't be able to close a workspace while there are still things pending") {
          client.close("ws1", failPendingDependencies = false).futureValue shouldBe false
          client.list().futureValue should contain only ("ws1")
        }

        // verify we still are awaiting that dependency
        withClue("the 'awaitWorkspace' which never is satisfied should still be pending after the failed close call") {
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
    "fail any pending awaitWorkspace calls" in {
      withWorkspaceClient { (client, _) =>
        val workspaceId = "fail any pending awaitWorkspace calls"
        val awaitFuture = client.awaitWorkspace(workspaceId, Set("won't ever arrive"), testTimeout.toMillis)
        client.close(workspaceId).futureValue shouldBe false

        val err = intercept[IllegalStateException] {
          Await.result(awaitFuture, testTimeout)
        }

        err.getMessage shouldBe s"Workspace '$workspaceId' has been closed"
      }
    }
    "delete the files in a workspace" in {
      withWorkspaceClient { (client, containerDir) =>
        client.upload("meh", Upload.forText("file", "content")).futureValue._2.exists() shouldBe true
        containerDir.children.size shouldBe 1

        client.upload("foo", Upload.forText("file", "content")).futureValue._2.exists() shouldBe true
        containerDir.children.size shouldBe 2

        // call the method under test
        client.close("foo").futureValue shouldBe true

        containerDir.children.size shouldBe 1

        // closing again shouldn't do owt
        client.close("foo").futureValue shouldBe false
      }
    }
  }
  "WorkspaceClient.awaitWorkspace" should {
    "return in error when not all uploads appear within a timeout" in {
      withWorkspaceClient { (client, containerDir) =>
        val workspaceId = "return in error when not all uploads appear within a timeout"

        client
          .upload(workspaceId, Upload.forText("i.wasUploaded", "123"))
          .futureValue
          ._2
          .exists() shouldBe true

        val awaitFuture = client.awaitWorkspace(workspaceId, Set("i.wasUploaded", "i.wasnt"), 1)
        val err = intercept[Exception] {
          Await.result(awaitFuture, testTimeout)
        }

        containerDir.children.map(_.fileName).toList shouldBe List(workspaceId)

        err.getMessage shouldBe s"Workspace '$workspaceId' timed out after 1 millisecond with dependency states: (i.wasUploaded,Ready),(i.wasnt,DoesNotExist)"
      }
    }
    "block until files are all available in a workspace" in {
      withWorkspaceClient { (client, containerDir) =>
        val workspaceId = "block until files are all available in a workspace"
        val dirFuture   = client.awaitWorkspace(workspaceId, Set("file.one", "file.two"), testTimeout.toMillis)
        dirFuture.isCompleted shouldBe false

        // upload one of the files
        client
          .upload(workspaceId, Upload.forText("file.one", "first file"))
          .futureValue
          ._2
          .exists() shouldBe true
        dirFuture.isCompleted shouldBe false

        // upload a second, but with a different name
        client
          .upload(workspaceId, Upload.forText("file.three", "another file"))
          .futureValue
          ._2
          .exists() shouldBe true
        dirFuture.isCompleted shouldBe false

        // upload file.two, but to a different session
        client
          .upload("wrong session", Upload.forText("file.two", "different session"))
          .futureValue
          ._2
          .exists() shouldBe true
        dirFuture.isCompleted shouldBe false

        // finally upload the file we expect in our '$workspaceId' session.
        client
          .upload(workspaceId, Upload.forText("file.two", "finally ready!"))
          .futureValue
          ._2
          .exists() shouldBe true

        val sessionDir = withClue(s"${containerDir.renderTree()}") {
          dirFuture.futureValue
        }

        sessionDir.children.map(_.fileName) should contain only ("file.one", "file.two", "file.three", ".file.one.metadata", ".file.three.metadata", ".file.two.metadata")

        sessionDir.resolve("file.two").text shouldBe "finally ready!"

        // the '$workspaceId' and 'wrong session' sessions
        containerDir.children.size shouldBe 2
      }

    }
  }
}
