package agora.exec.client

import java.util.UUID

import agora.BaseSpec
import agora.api.exchange.{JobPredicate, WorkSubscription}
import agora.exec.ExecConfig
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes
import agora.rest.RunningService

import scala.util.Properties

class RemoteRunnerTest extends BaseSpec with ProcessRunnerTCK {

  var runningWorker: RunningService[ExecConfig, ExecutionRoutes] = null
  var remoteRunner: ProcessRunner                                = null

  def runner: ProcessRunner = remoteRunner

  "ProcessRunner.run w/ saved result" should {

    "write down the output to a file" in {
      val workspace = UUID.randomUUID().toString

      val firstResults = runner.save(RunProcess("whoami").withWorkspace(workspace).withStdOutTo("whoami.out").withoutStreaming()).futureValue
      firstResults.exitCode shouldBe 0
      val expectedOutputFile = conf.uploadsDir.resolve(workspace).resolve("whoami.out")
      expectedOutputFile.exists shouldBe true
      expectedOutputFile.text.lines.mkString(" ").trim shouldBe Properties.userName
    }
  }

  "RemoteRunner.prepare" should {
    "match RunProcess jobs with a subscription ID" in {

      val baseSubscription: WorkSubscription = ExecConfig().runSubscription
      val details                            = baseSubscription.details
      details.append("workspaces", List("foo"))

      val sub1: WorkSubscription = baseSubscription.copy(details = details.append("workspaces", List("foo")))
      val sub2                   = baseSubscription.copy(details = details.append("workspaces", List("fizz", "bar")))
      val job1                   = RemoteRunner.prepare(RunProcess("hello").withWorkspace("foo"))
      val job2                   = RemoteRunner.prepare(RunProcess("hello").withWorkspace("bar"))

      JobPredicate().jobSubmissionDetailsMatchesWorkSubscription(job1, sub1) shouldBe true
      JobPredicate().jobSubmissionDetailsMatchesWorkSubscription(job1, sub2) shouldBe false
      JobPredicate().jobSubmissionDetailsMatchesWorkSubscription(job2, sub1) shouldBe false
      JobPredicate().jobSubmissionDetailsMatchesWorkSubscription(job2, sub2) shouldBe true
//
//      val differentJob = RemoteRunner.execAsJob(RunProcess("hello"), Option("456"))
//      JobPredicate().matches(differentJob, sub1) shouldBe false
//      JobPredicate().matches(differentJob, sub2) shouldBe true
    }
//    "match RunProcess jobs without a subscription ID" in {
//      val sub = ExecConfig().runSubscription.withSubscriptionKey("foo")
//      val job = RemoteRunner.execAsJob(RunProcess("hello"), None)
//      JobPredicate().matches(job, sub) shouldBe true
//    }
  }

  override def beforeAll = startAll

  override def afterAll = {
    stopAll
    conf.uploadsDir.toString should include("target/test/RemoteRunnerTest")
    conf.uploadsDir.delete()
  }

  val conf = ExecConfig("port=6666", "exchange.port=6666", "uploads.dir=target/test/RemoteRunnerTest")

  def startAll = {
    runningWorker = conf.start().futureValue
    remoteRunner = conf.remoteRunner()
  }

  def stopAll = {
    runningWorker.close()
    conf.clientConfig.cachedClients.close()
  }

}
