package agora.exec.workspace

import java.nio.file.Path

import agora.BaseSpec
import agora.rest.HasMaterializer
import agora.rest.logging.{BufferedAppender, LoggingOps}
import akka.actor.Props
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll
import org.slf4j.LoggerFactory

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.{Failure, Try}

class WorkspaceActorTest extends BaseSpec with HasMaterializer with BeforeAndAfterAll with StrictLogging {

  logger.trace("leave this line in -- Trying to get away w/ not having a SubstituteLoggerFactory impl when using LoggingOps")

  "WorkspaceActor.logger" should {
    "be able to be seen via LoggingOps" in {
      var bufferedAppender: BufferedAppender = null
      LoggingOps.withLogs("agora.exec.workspace.WorkspaceActor$", "trace") {
        case Some(appender) =>
          bufferedAppender = appender

          WorkspaceActor.actorLogger.trace("this is at trace")
          appender.logs shouldBe List("this is at trace")
          WorkspaceActor.actorLogger.error("bang")
          appender.logs shouldBe List("this is at trace", "bang")
        case None =>
          val ctxt = LoggerFactory.getILoggerFactory
          fail(s"Logger context is actually $ctxt")

      }

      withClue("The appender should've been removed outside of the 'withLogs' scope") {
        WorkspaceActor.actorLogger.trace("another trace")
        WorkspaceActor.actorLogger.error("another trace")
        bufferedAppender.logs shouldBe List("this is at trace", "bang")
      }
    }
  }

  "WorkspaceActor.await" should {
    "keep checking the .fileName.metadata comparison file until it matches the file size" in {

      withDir { dir =>
        LoggingOps.withLogs("agora.exec.workspace.WorkspaceActor") { appender =>
          //
          // 1) set up 'fileToCheck' to 'await' in our workspace.
          //
          val workspaceDir     = dir.resolve("test")
          val workspaceId      = workspaceDir.fileName
          val fileOfAGivenSize = workspaceDir.resolve("fileToCheck").text = "this is some file in the workspace"

          //
          // 2) Manually set up a .fileToCheck.metadata file of the wrong size.
          // Our belt-and-braces check will also be checking there is a '.fileToCheck.metadata' marker file
          // which contains the file's size to ensure it is completely written before our 'await' completes
          //
          val wrongSize    = fileOfAGivenSize.size - 1
          val metadataFile = MetadataFile.createMetadataFileFor(fileOfAGivenSize, expectedSizeOpt = Option(wrongSize))
          metadataFile.isDefined shouldBe true

          //
          // 3) Set up the workspace actor under test. When it sees the file doesn't match its expected size
          // as stated by the metadata file, it'll recheck in 150 millis (and then see it doesn't match, and then
          // poll again, until eventually timing out according to the AwaitWorkspace request)
          //
          val bytesReadyPollFrequency = 150.millis
          val workspaceActor          = system.actorOf(Props(new WorkspaceActor(workspaceId, workspaceDir, bytesReadyPollFrequency)))

          //
          // 4) call the method under test - and wait for 1 minute until the size works
          //
          val promise         = Promise[Path]()
          val timeoutInMillis = testTimeout.toMillis
          workspaceActor ! AwaitUploads(UploadDependencies(workspaceId, Set("fileToCheck"), timeoutInMillis, awaitFlushedOutput = true), promise)

          //
          // 5) let it poll for a bit before 'fixing' our metadata file
          //
          val waitTime = bytesReadyPollFrequency * 3
          Thread.sleep(waitTime.toMillis)
          promise.future.isCompleted shouldBe false
          metadataFile.get.text = fileOfAGivenSize.size.toString

          //
          // 6) our future should now complete
          //
          val path = promise.future.futureValue
          path shouldBe workspaceDir
        }
      }
    }
  }
  "WorkspaceActor.closeWorkspace" should {
    "remove files older than the ifNotModifiedSince time" in {
      withDir { dir =>
        val before = agora.api.time.now()

        val testFile = dir.resolve("file").text = "hi"
        WorkspaceActor.closeWorkspace(dir, Option(before.minusSeconds(2)), true, Set.empty) shouldBe false
        dir.nestedFiles().toList should contain only (testFile)

        WorkspaceActor.closeWorkspace(dir, Option(before.plusSeconds(2)), true, Set.empty) shouldBe true
        dir.nestedFiles().toList shouldBe empty
      }
    }
    "remove files if failPendingDependencies is specified when there are no dependencies" in {
      withDir { dir =>
        val before = agora.api.time.now()

        val testFile = dir.resolve("file").text = "hi"
        WorkspaceActor.closeWorkspace(dir, None, failPendingDependencies = false, Set(awaitUploads(dir.fileName))) shouldBe false
        dir.nestedFiles().toList should contain only (testFile)

        WorkspaceActor.closeWorkspace(dir, Option(before.plusSeconds(2)), failPendingDependencies = true, Set(awaitUploads(dir.fileName))) shouldBe true
        dir.nestedFiles().toList shouldBe empty
      }
    }
    "fail pending workspaces if told to close when they are pending" in {
      withDir { dir =>
        // create a file in our 'workspace' dir
        val before   = agora.api.time.now()
        val testFile = dir.resolve("file").text = "hi"

        // create a dependency to 'wait' on the workspace
        val dependency       = awaitUploads(dir.fileName)
        val dependencyFuture = dependency.workDirResult.future

        // now close it
        WorkspaceActor.closeWorkspace(dir, Option(before.plusSeconds(2)), failPendingDependencies = true, Set(dependency)) shouldBe true
        dir.nestedFiles().toList shouldBe empty

        // this future should fail, as we closed the workspace
        val Failure(error) = Try(dependencyFuture.block)
        val workspaceId    = dir.fileName
        error.getMessage shouldBe s"Workspace '${workspaceId}' has been closed"
      }
    }
  }

  def awaitUploads(workspaceId: WorkspaceId) = AwaitUploads(UploadDependencies(workspaceId, Set.empty, 0), Promise())
}
