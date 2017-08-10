package agora.exec.run

import agora.exec.ExecConfig
import agora.exec.model.RunProcessAndSave
import agora.exec.rest.ExecutionRoutes
import agora.rest.{BaseSpec, RunningService}
import org.scalatest.{AppendedClues, BeforeAndAfterAll}

import scala.util.Properties

class RemoteRunnerTest extends BaseSpec with BeforeAndAfterAll with AppendedClues { //with ProcessRunnerTCK {

  var runningWorker: RunningService[ExecConfig, ExecutionRoutes] = null
  var remoteRunner: ProcessRunner with AutoCloseable             = null

  def runner: ProcessRunner = remoteRunner

//  "manually enabled load test" should {
//
//    "stream a whole lot of results" in {
//      val firstResults = remoteRunner.run("bigOutput.sh".executable, srcDir.toAbsolutePath.toString, "1").futureValue
//      firstResults.size should be >= 10000
//    }
//  }

  "ProcessRunner.runAndSave" should {

    "write down the output to a file" in {
      val firstResults = runner.runAndSave(RunProcessAndSave(List("pwd"), "some workspace", "pwd.out")).futureValue
      firstResults.exitCode shouldBe 0
      val expectedOutputFile = conf.uploadsDir.resolve("some workspace").resolve("pwd.out")
      expectedOutputFile.exists shouldBe true
      expectedOutputFile.text shouldBe Properties.userDir
    }
  }

  override def beforeAll = startAll

  override def afterAll = stopAll

  val conf = ExecConfig("port=6666", "exchange.port=6666")

  def startAll = {
    runningWorker = conf.start().futureValue
    remoteRunner = conf.remoteRunner
  }

  def stopAll = {
    runningWorker.close()
    remoteRunner.close()
  }

}
