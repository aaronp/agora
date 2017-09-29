package agora.exec.rest

import java.util.UUID

import agora.BaseSpec
import agora.exec.events.{ReceivedJob, SystemEventMonitor}
import agora.exec.model._
import agora.exec.workspace.WorkspaceClient
import agora.rest.HasMaterializer
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

class ExecutionWorkflowTest extends BaseSpec with FailFastCirceSupport with HasMaterializer {

  implicit def runnerAsJob(rp: RunProcess): ReceivedJob = {
    ReceivedJob("", None, rp)
  }

  "ExecutionHandler.apply" should {

    "both stream and write output to a file" in {

      withDir { dir =>
        val client   = WorkspaceClient(dir, system)
        val workflow = ExecutionWorkflow(Map.empty, client, SystemEventMonitor.DevNull)

        val runProcess: RunProcess = RunProcess("echo", "gruess gott").withStdOutTo("std.out")

        // call the method under test
        val future         = workflow.onExecutionRequest(HttpRequest(), runProcess)
        val responseFuture = future.futureValue

        val StreamingResult(result) = runProcess.output.streaming.get.asResult(responseFuture)
        result.toList shouldBe List("gruess gott")

        withClue("the workspace directory should already exist") {
          val wsDir = client.await(runProcess.workspace, Set("std.out"), 0).futureValue
          wsDir.resolve("std.out").text shouldBe "gruess gott\n"
        }
      }
    }

    "return FileResult responses when streaming is not specified" in {
      withDir { dir =>
        val runProcess = RunProcess("echo", "hello world").withStdOutTo("std.out").withoutStreaming()

        val client   = WorkspaceClient(dir, system)
        val workflow = ExecutionWorkflow(Map.empty, client, SystemEventMonitor.DevNull)

        // call the method under test
        val responseFuture = workflow.onExecutionRequest(HttpRequest(), runProcess)

        val fileResult = responseFuture.flatMap(FileResult.apply).futureValue
        fileResult.exitCode shouldBe 0

        withClue("the workspace directory should already exist") {
          val wsDir = client.await(runProcess.workspace, Set("std.out"), 0).futureValue
          wsDir.resolve("std.out").text shouldBe "hello world\n"
        }
      }
    }

    "result in dependencies being triggered after a streaming process is executed" in {
      val httpResp                = verifyDependencies(identity)
      val StreamingResult(output) = StreamingSettings().asResult(httpResp)
      output.mkString(" ").trim shouldBe "hello there world"
    }

    "result in dependencies being triggered after a non-streaming process is executed" in {
      val httpResp = verifyDependencies(_.withoutStreaming)
      val result   = FileResult(httpResp).futureValue
      result.stdOutFile shouldBe None
    }
    "be able to access env variables" in {

      withDir { dir =>
        val runProcess =
          RunProcess("/bin/bash", "-c", "echo FOO is $FOO").withEnv("FOO", "bar").withStdOutTo("foo.txt")

        val client   = WorkspaceClient(dir, system)
        val workflow = ExecutionWorkflow(Map.empty, client, SystemEventMonitor.DevNull)
        // call the method under test
        val responseFuture = workflow.onExecutionRequest(HttpRequest(), runProcess).futureValue

        val StreamingResult(res) = runProcess.output.streaming.get.asResult(responseFuture)
        res.toList shouldBe List("FOO is bar")

        withClue("the workspace directory should already exist") {
          val wsDir = client.await(runProcess.workspace, Set("foo.txt"), 0).futureValue
          wsDir.resolve("foo.txt").text shouldBe "FOO is bar\n"
        }
      }
    }

    "execute a RunProcess with no MatchDetails or streaming" in {
      withDir { dir =>
        val workspaceId = UUID.randomUUID().toString
        val wsDir       = dir.resolve(workspaceId)

        wsDir.resolve("foo").text = "content"

        val arg        = RunProcess("cp", "foo", "bar").withWorkspace(workspaceId).withStdOutTo("std.out").withoutStreaming()
        val workspaces = WorkspaceClient(dir, system)
        val workflow   = ExecutionWorkflow(Map.empty, workspaces, SystemEventMonitor.DevNull)

        // call the method under test
        val httpResponse: HttpResponse = workflow.onExecutionRequest(HttpRequest(), arg).futureValue

        // verify the results
        httpResponse.status.intValue() shouldBe 200

        val response: FileResult = FileResult(httpResponse).futureValue
        response.stdOutFile shouldBe Some("std.out")
        response.workspaceId shouldBe workspaceId
        response.exitCode shouldBe 0

        wsDir.resolve("bar").exists shouldBe true
        wsDir.resolve("bar").text shouldBe "content"
      }
    }
  }

  def verifyDependencies(f: RunProcess => RunProcess): HttpResponse = {
    withDir { dir =>
      val client   = WorkspaceClient(dir, system)
      val workflow = ExecutionWorkflow(Map.empty, client, SystemEventMonitor.DevNull)

      // one process creates hello.world
      val echoHelloWorld = RunProcess("echo", "hello there world").withStdOutTo("hello.world")

      // and another depends on it... we run the dependency one first (out of order) to exercise
      // the WorkspaceClient
      val catHelloWorld =
        f(RunProcess("cat", "hello.world").withDependencies(echoHelloWorld.workspace, Set("hello.world"), testTimeout))

      // call the method under test
      val catFuture = workflow.onExecutionRequest(HttpRequest(), catHelloWorld)
      catFuture.isCompleted shouldBe false

      // now that we're awaiting a dependency on hello.world, execute the process which will create it
      val echoFuture = workflow.onExecutionRequest(HttpRequest(), echoHelloWorld).futureValue

      // specify a timeout of zero as it should exist
      val workspaceDir = client.await(echoHelloWorld.workspace, Set("hello.world"), 0).futureValue

      workspaceDir.resolve("hello.world").text shouldBe "hello there world\n"

      catFuture.futureValue
    }
  }

}
