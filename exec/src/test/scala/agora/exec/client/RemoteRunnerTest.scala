package agora.exec.client

import java.util.UUID

import agora.BaseSpec
import agora.exec.ExecConfig
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes
import agora.rest.RunningService

import scala.util.Properties

class RemoteRunnerTest extends BaseSpec with ProcessRunnerTCK {

  var runningWorker: RunningService[ExecConfig, ExecutionRoutes] = null
  var remoteRunner: ProcessRunner = null

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
