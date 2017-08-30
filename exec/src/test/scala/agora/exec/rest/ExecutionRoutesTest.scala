package agora.exec.rest

import java.util.UUID

import agora.api.exchange.Exchange
import agora.exec.ExecConfig
import agora.exec.model.{ExecuteProcess, ResultSavingRunProcessResponse, RunProcess, Upload}
import agora.exec.run.ExecutionClient
import agora.exec.workspace.WorkspaceClient
import agora.io.Sources
import agora.rest.BaseRoutesSpec
import io.circe.generic.auto._

class ExecutionRoutesTest extends BaseRoutesSpec {
  "POST /rest/exec/run" should {
    "execute commands" in {
      withDir { dir =>
        val workspaces = WorkspaceClient(dir, system)
        val er         = ExecutionRoutes(ExecConfig(), Exchange.instance(), workspaces)

        val txt = UUID.randomUUID().toString
        ExecutionClient.asRequest(RunProcess("echo", txt)) ~> er.executeRoute ~> check {
          val content = Sources.asText(responseEntity.dataBytes).futureValue
          content.lines.mkString("") shouldBe txt
        }
      }
    }
  }
  "POST /rest/exec/save" should {
    "execute commands" in {
      withDir { dir =>
        val workspaces = WorkspaceClient(dir, system)
        val er         = ExecutionRoutes(ExecConfig(), Exchange.instance(), workspaces)

        val request = ExecutionClient.asRequest(ExecuteProcess(List("cp", "x", "y"), "ws"))
        // upload file 'x'
        workspaces.upload("ws", Upload.forText("x", "text")).futureValue shouldBe true

        request ~> er.executeAndSaveRoute ~> check {
          val resp = responseAs[ResultSavingRunProcessResponse]
          resp.exitCode shouldBe 0
          dir.resolve("ws").resolve("y").text shouldBe "text"
        }
      }
    }
  }
}
