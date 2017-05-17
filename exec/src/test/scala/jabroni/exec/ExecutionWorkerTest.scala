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


  "ExecutionRoutes" should {

    "stream results" in {
      val srcDir = {
        def jabroni(p: Path): Path = {
          if (p.fileName == "jabroni") p else {
            p.parent.map(jabroni).getOrElse(sys.error("Hit file root looking for source root"))
          }
        }

        jabroni(Properties.userDir.asPath)
      }
      println("Running under " + srcDir)

      val runMe = "bigOutput.sh".executable
      val firstResults = remoteRunner.run(runMe, srcDir.toAbsolutePath.toString, "1").futureValue
      val all = firstResults.toList
      all.size should be > 10
    }
    "return the error output when the worker returns a specified error code" ignore {
      val firstResults = remoteRunner.run("throwError.sh".executable, "123").futureValue

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
  }
  "ExecutionRoutes handler" should {

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
