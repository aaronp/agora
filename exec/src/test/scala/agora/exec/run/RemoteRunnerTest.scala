package agora.exec.run

import agora.exec.ExecConfig
import agora.exec.rest.ExecutionRoutes
import agora.rest.test.TestUtils._
import agora.rest.{BaseSpec, RunningService}
import org.scalatest.{AppendedClues, BeforeAndAfterAll}

class RemoteRunnerTest extends BaseSpec with BeforeAndAfterAll with AppendedClues with ProcessRunnerTCK {

  var runningWorker: RunningService[ExecConfig, ExecutionRoutes] = null
  var remoteRunner: ProcessRunner with AutoCloseable             = null

  def runner: ProcessRunner = remoteRunner

  "manually enabled load test" ignore {

    "stream a whole lot of results" in {
      val firstResults = remoteRunner.run("bigOutput.sh".executable, srcDir.toAbsolutePath.toString, "1").futureValue
      firstResults.size should be >= 10000
    }
  }

  override def beforeAll = startAll

  override def afterAll = stopAll

  val conf = ExecConfig()

  def startAll = {
    runningWorker = conf.start().futureValue
    remoteRunner = conf.remoteRunner
  }

  def stopAll = {
    runningWorker.close()
    remoteRunner.close()
  }

}
