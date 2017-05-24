package jabroni.exec

import java.nio.charset.StandardCharsets

import jabroni.rest.test.TestUtils._
import jabroni.rest.{BaseSpec, RunningService}
import StandardCharsets._

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import jabroni.domain.io.Sources
import jabroni.rest.test.TestUtils
import org.scalatest.{AppendedClues, BeforeAndAfterAll}

class RemoteRunnerTest extends BaseSpec with BeforeAndAfterAll with AppendedClues with ProcessRunnerTCK {

  var runningWorker: RunningService[ExecConfig, ExecutionRoutes] = null
  var remoteRunner: ProcessRunner with AutoCloseable = null

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
        Upload(fileName, fileName.absolutePath.size, fileName.asSource)
      }

      val KeyValue = "(.+)=(.*)".r
      val output = remoteRunner.run(RunProcess(List("sh", "-c", "env")), uploads).futureValue.toList.sorted
      val envOutput = output.collect {
        case KeyValue(k, v) => (k, v)
      }.toMap

      withClue("filenames should be upper-cased and underscored against their qualified paths") {
        envOutput.keySet should contain allOf("UTF_16_BE", "ISO_8859_1_TXT")
        envOutput("UTF_16_BE").asPath.isFile shouldBe true
        envOutput("ISO_8859_1_TXT").asPath.isFile shouldBe true

        val actualutf16 = envOutput("UTF_16_BE").asPath.getText(UTF_16BE)
        envOutput("ISO_8859_1_TXT").asPath.getText(ISO_8859_1) should include("This is in ISO-8859-1")
      }
    }
    "be able to operate on large uploads" in {

      TestUtils.withMaterializer { implicit mat =>

        val text = "This is a lot of text\n" * 10000
        val src: Source[ByteString, Any] = Source.single(ByteString(text))
        val len = Sources.sizeOf(src).futureValue

        val bigUploads = (0 to 1).map(i => Upload(s"MYUPLOAD$i", len, src)).toList
        val command: List[String] = List("concat.sh".executable, " MYUPLOAD0")
        val output = remoteRunner.run(RunProcess(command), bigUploads).futureValue
        output.mkString("", "\n", "\n") shouldBe text
      }
    }

    "be able execute things which operate on several uploads" in {
      val uploads = List("UTF-16-BE", "iso-8859-1.txt").map { fileName =>
        Upload(fileName, fileName.absolutePath.size, fileName.asSource)
      }

      val resultFuture = remoteRunner.run(RunProcess(List("bash", "-c", "concat.sh".executable + " $UTF_16_BE $ISO_8859_1_TXT",
        "UTF-16-BE", "iso-8859-1.txt", "bigOutput.sh")), uploads)
      val lines = resultFuture.futureValue.mkString("\n")
      lines should include("This is in ISO-8859-1")
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
