package agora.exec.workspace

import agora.BaseSpec
import agora.rest.HasMaterializer

import scala.concurrent.Promise
import scala.util.{Failure, Try}

class WorkspaceActorTest extends BaseSpec with HasMaterializer {

  "WorkspaceActor.closeWorkspace" should {
    "remove files older than the ifNotModifiedSince time" in {
      withDir { dir =>

        val before = agora.api.time.now()

        val testFile = dir.resolve("file").text = "hi"
        WorkspaceActor.closeWorkspace(dir, Option(before.minusSeconds(2)), true, Nil) shouldBe false
        dir.nestedFiles().toList should contain only (testFile)

        WorkspaceActor.closeWorkspace(dir, Option(before.plusSeconds(2)), true, Nil) shouldBe true
        dir.nestedFiles().toList shouldBe empty
      }
    }
    "remove files if failPendingDependencies is specified when there are no dependencies" in {
      withDir { dir =>

        val before = agora.api.time.now()

        val testFile = dir.resolve("file").text = "hi"
        WorkspaceActor.closeWorkspace(dir, None, failPendingDependencies = false, List(awaitUploads(dir.fileName))) shouldBe false
        dir.nestedFiles().toList should contain only (testFile)

        WorkspaceActor.closeWorkspace(dir, Option(before.plusSeconds(2)), failPendingDependencies = true, List(awaitUploads(dir.fileName))) shouldBe true
        dir.nestedFiles().toList shouldBe empty
      }
    }
    "fail pending workspaces if told to close when they are pending" in {
      withDir { dir =>

        // create a file in our 'workspace' dir
        val before = agora.api.time.now()
        val testFile = dir.resolve("file").text = "hi"

        // create a dependency to 'wait' on the workspace
        val dependency = awaitUploads(dir.fileName)
        val dependencyFuture = dependency.workDirResult.future

        // now close it
        WorkspaceActor.closeWorkspace(dir, Option(before.plusSeconds(2)), failPendingDependencies = true, List(dependency)) shouldBe true
        dir.nestedFiles().toList shouldBe empty

        // this future should fail, as we closed the workspace
        val Failure(error) = Try(dependencyFuture.block)
        val workspaceId = dir.fileName
        error.getMessage shouldBe s"Workspace '${workspaceId}' has been closed"
      }
    }
  }

  def awaitUploads(workspaceId: WorkspaceId) = AwaitUploads(UploadDependencies(workspaceId, Set.empty, 0), Promise())
}
