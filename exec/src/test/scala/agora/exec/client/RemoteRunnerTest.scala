package agora.exec.client

import java.util.UUID

import agora.exec.model.RunProcess
import agora.exec.{ExecBoot, ExecConfig}
import agora.rest.{AkkaImplicits, RunningService}
import agora.{BaseIOSpec, BaseExecSpec}

import scala.concurrent.Future
import scala.util.Properties

class RemoteRunnerTest extends BaseExecSpec with ProcessRunnerTCK {

  var runningWorker: RunningService[ExecConfig, ExecBoot] = null
  var remoteRunner: ProcessRunner                         = null

  def runner: ProcessRunner = remoteRunner

  "ProcessRunner.run w/ saved result" should {

    "write down the output to a file" in {
      val workspace = UUID.randomUUID().toString

      val firstResults = runner
        .save(RunProcess("whoami").withWorkspace(workspace).withStdOutTo("whoami.out").withoutStreaming())
        .futureValue
      firstResults.exitCode shouldBe 0
      val expectedOutputFile = conf.uploadsDir.resolve(workspace).resolve("whoami.out")
      expectedOutputFile.exists() shouldBe true
      expectedOutputFile.text.lines.mkString(" ").trim shouldBe Properties.userName
    }
  }

  override def beforeAll = startAll

  override def afterAll = {
    stopAll
    conf.uploadsDir.toString should include("target/test/RemoteRunnerTest")
    conf.uploadsDir.delete()
  }

  val conf = ExecConfig("port=6666", "exchange.port=6666", s"workspaces.dir=${BaseIOSpec.nextTestDir("RemoteRunnerTest")}")

  def startAll = {
    runningWorker = conf.start().futureValue
    remoteRunner = conf.remoteRunner()
  }

  def stopAll = {
    runningWorker.stop().futureValue
    conf.stop().futureValue
  }

}
