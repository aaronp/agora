package agora.exec.rest

import java.util.UUID

import agora.api.exchange.{Exchange, JobPredicate, WorkSubscription}
import agora.api.json.JPath
import agora.api.worker.{HostLocation, WorkerDetails}
import agora.exec.ExecConfig
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes._
import agora.exec.run.{ExecutionClient, RemoteRunner}
import agora.exec.workspace.WorkspaceClient
import agora.io.Sources
import agora.rest.BaseRoutesSpec
import agora.rest.worker.WorkerConfig

class ExecutionRoutesTest extends BaseRoutesSpec {
//  -  def execSubscription(config: WorkerConfig): WorkSubscription = {
//    -    // format: off
//      -    val sub = config.subscription.
//      -      withPath("/rest/exec/run").
//      -      append("topic", "execute").
//      -      matchingJob(JPath("command").asMatcher)
//    -    // format: on
//      -    assert(execCriteria.matches(sub.details.aboutMe))
//    -    sub
//    -  }
//  "ExecutionRoutes.execSubscription" should {
//    "match jobs produced from RemoteRunner.execAsJob" in {
//
//      // format: off
//      val expectedSubscription = WorkSubscription.localhost(7770).
//        withPath("/rest/exec/run").
//        append("topic", "execute").
//        matchingJob(JPath("command").asMatcher)
//      // format: on
//
//      val config             = ExecConfig()
//      val actualSubscription = config.subscription
//
//      //      val execSubscription = ExecutionRoutes.execSubscription()
//      val job = RemoteRunner.execAsJob(RunProcess("hello"), None)
//      JobPredicate().matches(job, actualSubscription) shouldBe true
//
//      execCriteria.matches(expectedSubscription.details.aboutMe) shouldBe true
//    }
//    "match jobs against a worker with a specific subscription" in {
//
//      val sub1 = execSubscription(WorkerConfig()).withSubscriptionKey("specificKey")
//      val sub2 = execSubscription(WorkerConfig()).withSubscriptionKey("anotherKey")
//
//      //      val execSubscription = ExecutionRoutes.execSubscription()
//      val job = RemoteRunner.execAsJob(RunProcess("hello"), Some("specificKey"))
//      JobPredicate().matches(job, sub1) shouldBe true
//      JobPredicate().matches(job, sub2) shouldBe false
//
//      val job2 = RemoteRunner.execAsJob(RunProcess("hello"), Some("anotherKey"))
//      JobPredicate().matches(job2, sub1) shouldBe false
//      JobPredicate().matches(job2, sub2) shouldBe true
//    }
//  }
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
