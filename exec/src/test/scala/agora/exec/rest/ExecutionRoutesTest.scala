package agora.exec.rest

import java.util.UUID

import agora.api.`match`.MatchDetails
import agora.api.exchange.Exchange
import agora.exec.ExecConfig
import agora.exec.client.ExecutionClient
import agora.exec.model._
import agora.exec.workspace.WorkspaceClient
import agora.io.Sources
import agora.rest.{BaseRoutesSpec, CommonRequestBuilding}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import io.circe.generic.auto._

class ExecutionRoutesTest extends BaseRoutesSpec with CommonRequestBuilding {
  "DELETE /rest/exec/cancel" should {
    "return false the second time it tries to cancel the same job" in {
      withDir { dir =>
        val er = ExecutionRoutes(ExecConfig())

        // start a job
        val jobId      = "foo"
        val yes        = RunProcess("yes")
        val md         = MatchDetails.empty.copy(jobId = jobId)
        val respFuture = er.executeHandler.onExecutionRequest(HttpRequest().withCommonHeaders(Option(md)), yes)

        // first cancel
        ExecutionClient.asCancelRequest(jobId) ~> er.routes(None) ~> check {
          response.status.intValue() shouldBe 200
          responseAs[String] shouldBe "true"
        }

        // second cancel
        ExecutionClient.asCancelRequest(jobId) ~> er.routes(None) ~> check {
          response.status.intValue() shouldBe 200
          responseAs[String] shouldBe "false"
        }

        val yesResponse: HttpResponse = respFuture.futureValue
        println(yesResponse)
        val content = Sources.asText(yesResponse.entity.dataBytes).futureValue
        println(content)
      }
    }
    "return a 404 for unknown jobs" in {
      withDir { dir =>
        val er = ExecutionRoutes(ExecConfig())

        ExecutionClient.asCancelRequest("doesnt't exist") ~> er.routes(None) ~> check {
          response.status.intValue() shouldBe 404
          responseAs[String] shouldBe "meh"
        }
      }
    }
  }
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
        workspaces.upload("ws", Upload.forText("x", "text")).futureValue.exists() shouldBe true

        request ~> er.executeRoute ~> check {
          val resp = responseAs[FileResult]
          resp.exitCode shouldBe 0
          dir.resolve("ws").resolve("y").text shouldBe "text"
        }
      }
    }
  }

}
