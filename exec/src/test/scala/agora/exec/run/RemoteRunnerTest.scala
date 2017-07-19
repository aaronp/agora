package agora.exec.run

import java.nio.charset.StandardCharsets._

import akka.stream.scaladsl.Source
import akka.util.ByteString
import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.exec.rest.ExecutionRoutes
import agora.io.Sources
import agora.rest.test.TestUtils
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

  "RemoteRunner" should {

    "be able to access the uploads and working directory via environment variables" in {
      val uploads = List("UTF-16-BE", "iso-8859-1.txt").map { fileName =>
        Upload(fileName, fileName.asSource, Option(fileName.absolutePath.size))
      }

      //uploads

      val KeyValue = "(.+)=(.*)".r
      val output   = remoteRunner.run(RunProcess(List("sh", "-c", "env"))).futureValue.toList.sorted
      val envOutput = output.collect {
        case KeyValue(k, v) => (k, v)
      }.toMap

      withClue("filenames should be upper-cased and underscored against their qualified paths") {
        envOutput.keySet should contain allOf ("UTF_16_BE", "ISO_8859_1_TXT")
        envOutput("UTF_16_BE").asPath.isFile shouldBe true
        envOutput("ISO_8859_1_TXT").asPath.isFile shouldBe true

        val actualutf16 = envOutput("UTF_16_BE").asPath.getText(UTF_16BE)
        envOutput("ISO_8859_1_TXT").asPath.getText(ISO_8859_1) should include("This is in ISO-8859-1")
      }
    }
  }

  override def beforeAll = startAll

  override def afterAll = stopAll

  val conf = ExecConfig()

  def startAll = {
    runningWorker = conf.start().futureValue
    remoteRunner = conf.remoteRunner()
  }

  def stopAll = {
    runningWorker.close()
    remoteRunner.close()
  }

}
