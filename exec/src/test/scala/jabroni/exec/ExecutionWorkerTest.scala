package jabroni.exec

import akka.stream.scaladsl.Source
import akka.util.ByteString
import jabroni.rest.BaseSpec
import jabroni.rest.worker.WorkerConfig.RunningWorker
import org.scalatest.BeforeAndAfterAll

class ExecutionWorkerTest extends BaseSpec with BeforeAndAfterAll {

  var runningWorker: RunningWorker = null
  var remoteRunner: ProcessRunner with AutoCloseable = null

  override def beforeAll = {
    super.beforeAll()
    startAll
  }

  override def afterAll = {
    super.afterAll()
    stopAll
  }

  def startAll = {
    val conf = ExecConfig()
    runningWorker = conf.start.futureValue
    remoteRunner = conf.remoteRunner()
  }

  def stopAll = {
    runningWorker.close()
    remoteRunner.close()
    runningWorker = null
    remoteRunner = null
  }

  "ExecutionWorker" should {

    "run simple commands remotely" in {
      val res: Iterator[String] = remoteRunner.run("echo", "testing 123").futureValue
      res.mkString(" ") shouldBe "testing 123"
    }
    "run commands which operate on uploads" in {
      val content = ByteString("This is the content\nof my very special\nupload")
      val src = Source.single(content)
      val myUpload = Upload("my.upload", content.length, src)
      val res: Iterator[String] = remoteRunner.run(RunProcess("cat", "my.upload"), List(myUpload)).futureValue
      res.mkString("\n") shouldBe content.utf8String
    }
    "run commands which operate on environment variables" in {
      val res: Iterator[String] = remoteRunner.run(RunProcess("bash", "-c", "echo FOO is $FOO").withEnv("FOO", "bar"), Nil).futureValue
      res.mkString("\n") shouldBe "FOO is bar"
    }
  }
}
