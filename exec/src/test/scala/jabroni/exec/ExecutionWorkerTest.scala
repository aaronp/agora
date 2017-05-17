package jabroni.exec

import java.nio.file.Path

import akka.stream.scaladsl.Source
import akka.util.ByteString
import jabroni.rest.BaseSpec
import jabroni.rest.worker.WorkerConfig.RunningWorker
import org.scalatest.BeforeAndAfterAll
import jabroni.rest.test.TestUtils._

import scala.util.Properties

class ExecutionWorkerTest extends BaseSpec with BeforeAndAfterAll {

  var runningWorker: RunningWorker = null
  var remoteRunner: ProcessRunner with AutoCloseable = null


  "load test" ignore {

    "stream a whole lot of results" in {
      val firstResults = remoteRunner.run("bigOutput.sh".executable, srcDir.toAbsolutePath.toString, "1000").futureValue
      firstResults.foreach(println)
      println("done")
    }
  }

  "ExecutionRoutes" should {

    "stream results" in {
      val firstResults = remoteRunner.run("bigOutput.sh".executable, "1").futureValue
      val all = firstResults.toList
      all.size should be > 10
    }
    "return the error output when the worker returns a specified error code" in {
      val firstResults = remoteRunner.run("throwError.sh".executable, "123").futureValue
      firstResults.toList shouldBe List("first info output", "second info output", "about to exit with 123", "first error output", "second error output")
    }

    "be able to specify acceptable return codes" in {
      // like the above test, but doesn't include the stderr as '123' is our success code
      val firstResults = remoteRunner.run(RunProcess(List("throwError.sh".executable, "123"), successExitCodes = Set(123))).futureValue
      firstResults.toList shouldBe List("first info output", "second info output", "about to exit with 123")
    }

    "run multiple processes concurrently" ignore {
      val secondWorkerConf = ExecConfig(Array("port=" + (conf.port + 1)))
      val secondWorker = secondWorkerConf.start.futureValue
      try {
        val firstResults = remoteRunner.run("printRange.sh".executable, "1", "1000", "0").futureValue
        secondWorker

      } finally {

      }

    }

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

  override def beforeAll = {
    super.beforeAll()
    startAll
  }

  override def afterAll = {
    super.afterAll()
    stopAll
  }

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
