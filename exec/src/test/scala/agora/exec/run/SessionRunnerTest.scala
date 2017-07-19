package agora.exec.run

import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.rest.BaseSpec
import agora.rest.worker.WorkerConfig.RunningWorker

class SessionRunnerTest extends BaseSpec {

  var runningWorker: RunningWorker = null
  val conf                         = ExecConfig.load()

  override def beforeAll(): Unit = {
    super.beforeAll()
    runningWorker = conf.startWorker().futureValue
    conf.exchangeClient
  }

  override def afterAll(): Unit = {
    super.afterAll()
    runningWorker.stop()
  }

  "SessionRunner" should {
    "be able to upload a file and then execute a commands against that file" in {
//      val runner                = conf.sessionRunner
//      val uploadFuture          = runner.upload("delinquent session", Upload.forText("some upload", "the content"))
//      val startFuture           = runner.startSession("delinquent session")
//      val result: ProcessOutput = runner.run("delinquent session", RunProcess("ls"), Set("some upload"))
//
//      val output = result.futureValue
//
//      val str = output.mkString("\n")
//
//      println(str)
    }
    "be able to execute a commands against that file which is uploaded after the exec is submitted" in {}
    "not be able to execute operation for a file which doesn't exist" in {}
    "be able to start the session after session commands have been received" in {}
  }
}
