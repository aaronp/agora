package agora.exec.rest

import java.util.UUID

import agora.api.exchange.{Exchange, JobPredicate, WorkSubscription}
import agora.api.json.JPath
import agora.api.worker.{HostLocation, WorkerDetails}
import agora.exec.ExecConfig
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes.execCriteria
import agora.exec.run.{ExecutionClient, RemoteRunner}
import agora.exec.workspace.WorkspaceClient
import agora.io.Sources
import agora.rest.BaseRoutesSpec

class ExecutionRoutesTest extends BaseRoutesSpec {

  "ExecutionRoutes.execSubscription" should {
    "match jobs produced from RemoteRunner.execAsJob" in {

      // format: off
      val expectedSubscription = WorkSubscription(details = WorkerDetails(location = HostLocation("localhost", 7770))).
        withPath("/rest/exec/run").
        append("topic", "execute").
        matchingJob(JPath("command").asMatcher)
      // format: on
      val x = JPath("command")
      println(x.json.spaces2)

      println()
      println(expectedSubscription.details)
      println()

      val config = ExecConfig()

      val actualSubscription = config.subscription

      println()
      println(actualSubscription.details)
      println()

      //      val execSubscription = ExecutionRoutes.execSubscription()
      val job = RemoteRunner.execAsJob(RunProcess("hello"))
      JobPredicate().matches(job, actualSubscription) shouldBe true

      execCriteria.matches(expectedSubscription.details.aboutMe) shouldBe true
    }
  }
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
}
