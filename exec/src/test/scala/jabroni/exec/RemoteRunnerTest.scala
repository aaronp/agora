package jabroni.exec

import jabroni.rest.BaseSpec
import jabroni.rest.test.TestUtils._
import jabroni.rest.worker.WorkerConfig.RunningWorker
import org.scalatest.BeforeAndAfterAll

class RemoteRunnerTest extends BaseSpec with ProcessRunnerTCK with BeforeAndAfterAll {

  var runningWorker: RunningWorker = null
  var remoteRunner: ProcessRunner with AutoCloseable = null

  override def runner: ProcessRunner = remoteRunner

  "manually enabled load test" ignore {

    "stream a whole lot of results" in {
      val firstResults = remoteRunner.run("bigOutput.sh".executable, srcDir.toAbsolutePath.toString, "1000").futureValue
      firstResults.foreach(println)
      println("done")
    }
  }


  override def beforeAll = startAll

  override def afterAll = stopAll

  val conf = ExecConfig()

  def startAll = {
    runningWorker = conf.start.futureValue
    remoteRunner = conf.remoteRunner()
  }

  def stopAll = {
    runningWorker.close()
    remoteRunner.close()
    runningWorker = null
    remoteRunner = null
  }

}
