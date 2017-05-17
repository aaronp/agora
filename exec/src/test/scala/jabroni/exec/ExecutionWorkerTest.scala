package jabroni.exec

import java.net.URL
import java.nio.file.Paths

import akka.stream.scaladsl.Source
import akka.util.ByteString
import jabroni.rest.BaseSpec
import jabroni.rest.worker.WorkerConfig.RunningWorker
import org.scalatest.BeforeAndAfterAll

class ExecutionWorkerTest extends BaseSpec with BeforeAndAfterAll {

  var runningWorker: RunningWorker = null
  var remoteRunner: ProcessRunner with AutoCloseable = null

  implicit class RichString(resource: String) {

    def onClasspath: URL = {
      val url = getClass.getClassLoader.getResource(resource)
      require(url != null, s"Couldn't find $resource")
      url
    }

    def absolutePath = Paths.get(onClasspath.toURI).toAbsolutePath.toString
  }


  "ExecutionRoutes" should {


    "stream results" in {
      val firstResults = remoteRunner.run("bigOutput.sh".absolutePath, "1").futureValue
      firstResults.foreach(println)
      println("")

    }
    "return the error output when the worker returns a specified error code" ignore {
      val firstResults = remoteRunner.run("throwError.sh".absolutePath, "123").futureValue

    }
    "run multiple processes concurrently" ignore {
      val secondWorkerConf = ExecConfig(Array("port=" + (conf.port + 1)))
      val secondWorker = secondWorkerConf.start.futureValue
      try {
        val firstResults = remoteRunner.run("printRange.sh".absolutePath, "1", "1000", "0").futureValue
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
