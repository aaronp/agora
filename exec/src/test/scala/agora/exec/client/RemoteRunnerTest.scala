package agora.exec.client

import java.util.UUID

import agora.api.BaseSpec
import agora.exec.ExecConfig
import agora.exec.model.ExecuteProcess
import agora.exec.rest.ExecutionRoutes
import agora.rest.RunningService

import scala.util.Properties

class RemoteRunnerTest extends BaseSpec with ProcessRunnerTCK {

  var runningWorker: RunningService[ExecConfig, ExecutionRoutes] = null
  var remoteRunner: ProcessRunner with AutoCloseable             = null

  def runner: ProcessRunner = remoteRunner

  "ProcessRunner.run w/ saved result" should {

    "write down the output to a file" in {
      val workspace = UUID.randomUUID().toString

      val firstResults = runner.run(ExecuteProcess(List("whoami"), workspace, "whoami.out")).futureValue
      firstResults.exitCode shouldBe 0
      val expectedOutputFile = conf.uploadsDir.resolve(workspace).resolve("whoami.out")
      expectedOutputFile.exists shouldBe true
      expectedOutputFile.text.lines.mkString(" ").trim shouldBe Properties.userName
    }
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
    remoteRunner = conf.remoteRunner
  }

  def stopAll = {
    runningWorker.close()
    remoteRunner.close()
  }

}
