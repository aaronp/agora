package agora.exec.rest

import java.util.UUID

import agora.api.exchange.Exchange
import agora.exec.ExecConfig
import agora.exec.client.ExecutionClient
import agora.exec.model._
import agora.exec.workspace.WorkspaceClient
import agora.io.Sources
import agora.rest.BaseRoutesSpec
import io.circe.generic.auto._
import org.scalatest.BeforeAndAfterAll

class ExecutionRoutesTest extends BaseRoutesSpec with BeforeAndAfterAll {
  "POST /rest/exec/run" should {
    "execute streaming commands" in {
      withDir { dir =>
        val er = ExecutionRoutes(ExecConfig())

        val txt = UUID.randomUUID().toString
        ExecutionClient.asRequest(RunProcess("echo", txt)) ~> er.executeRoute ~> check {
          val content = Sources.asText(responseEntity.dataBytes).futureValue
          content.lines.mkString("") shouldBe txt
        }
      }
    }
    "execute non-streaming commands" in {
      withDir { dir =>
        val workspaces = WorkspaceClient(dir, system)

        val execConfig = ExecConfig()
        val workflow   = ExecutionWorkflow(execConfig.defaultEnv, workspaces, execConfig.eventMonitor)
        val er         = ExecutionRoutes(execConfig, Exchange.instance(), workflow)

        val request = ExecutionClient.asRequest(RunProcess("cp", "x", "y").withoutStreaming().withWorkspace("ws"))
        // upload file 'x'
        workspaces.upload("ws", Upload.forText("x", "text")).futureValue shouldBe true

        request ~> er.executeRoute ~> check {
          val resp = responseAs[FileResult]
          resp.exitCode shouldBe 0
          dir.resolve("ws").resolve("y").text shouldBe "text"
        }
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }
}
