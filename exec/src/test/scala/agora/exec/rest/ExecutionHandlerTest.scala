package agora.exec.rest

import java.util.UUID

import agora.exec.ExecConfig
import agora.exec.model.{RunProcessAndSave, RunProcessAndSaveResponse}
import agora.exec.workspace.WorkspaceClient
import agora.rest.BaseSpec

class ExecutionHandlerTest extends BaseSpec {

  "ExecutionHandler.executeAndSave" should {
    "execute the RunProcessAndSave argument with no MatchDetails" in {
      withDir { dir =>
        val workspaceId = UUID.randomUUID().toString
        val wsDir       = dir.resolve(workspaceId)

        wsDir.resolve("foo").text = "content"

        val config = ExecConfig()
        import config.serverImplicits._

        val arg        = RunProcessAndSave(List("cp", "foo", "bar"), workspaceId)
        val workspaces = WorkspaceClient(dir, config.serverImplicits.system)

        // call the method under test
        val response: RunProcessAndSaveResponse = ExecutionHandler.executeAndSave(config, workspaces, arg, None).futureValue

        // verify the results
        response.exitCode shouldBe 0
        response.fileName shouldBe "std.out"

        wsDir.resolve("bar").exists shouldBe true
        wsDir.resolve("bar").text shouldBe "content"
      }
    }
  }

}
