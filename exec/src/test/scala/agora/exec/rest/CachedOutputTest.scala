package agora.exec.rest

import java.nio.file.Path

import agora.BaseExecSpec
import agora.exec.model._
import agora.rest.HasMaterializer
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

class CachedOutputTest extends BaseExecSpec with HasMaterializer {

  def asFileResult(cachedResponseOpt: Option[Future[HttpResponse]]): FileResult = {
    cachedResponseOpt.isDefined shouldBe true
    cachedResponseOpt.get.flatMap(FileResult.apply).futureValue
  }

  "CachedOutput.cachedResponse" should {
    "return None if the cached output files don't exist for non-streaming requests" in {
      withDir { workingDir =>
        val inputProcess = RunProcess(List("hello", "world")).withCaching(true).withoutStreaming()

        CachedOutput.cachedResponse(workingDir, HttpRequest(), inputProcess) shouldBe None
      }
    }
    "return None if the cached output files don't exist for streaming requests" in {
      withDir { workingDir =>
        val inputProcess = RunProcess(List("hello", "world"))
          .withCaching(true)
          .withStreamingSettings(StreamingSettings())
          .ensuringCacheOutputs

        CachedOutput.cachedResponse(workingDir, HttpRequest(), inputProcess) shouldBe None
      }
    }
    "return the stdout when the cached output files exist for non-streaming requests" in {
      withDir { workingDir =>
        val inputProcess = RunProcess(List("hello", "world")).withCaching(true).withoutStreaming().ensuringCacheOutputs
        val cacheDir     = CachedOutput.cacheDir(workingDir, inputProcess)
        CachedOutput.cache(cacheDir, inputProcess, 123)

        // actually create the files so they exist:
        workingDir.resolve(inputProcess.output.stdOutFileName.get).text = "the is std out results"
        workingDir.resolve(inputProcess.output.stdErrFileName.get).text = "the is std err results"

        val cachedResponseOpt      = CachedOutput.cachedResponse(workingDir, HttpRequest(), inputProcess).map(_._2)
        val fileResult: FileResult = asFileResult(cachedResponseOpt)
        fileResult shouldBe FileResult(123, inputProcess.workspace, inputProcess.output.stdOutFileName, inputProcess.output.stdErrFileName, None)
      }
    }
    "return the stdout when the cached output files exist for streaming requests" in {
      withDir { workingDir =>
        val streamingResult = getCachedStreamingResults(workingDir, 456, 456)

        streamingResult.output.mkString("") shouldBe "the is std out results"
      }
    }
    "return the stderr when the cached output files exist with an error response for streaming requests" in {
      withDir { workingDir =>
        val streamingResult = getCachedStreamingResults(workingDir, 456, 123)

        val exp = intercept[ProcessException] {
          val outputIter = streamingResult.output

          outputIter.next() shouldBe "the is std out results"
          outputIter.size
        }

        exp.error.exitCode shouldBe Some(456)
        exp.error.stdErr.mkString("") shouldBe "the is std err results"
      }
    }
  }

  def getCachedStreamingResults(workingDir: Path, exitCode: Int, successExitCode: Int) = {
    val inputProcess = RunProcess(List("hello", "world"))
      .withCaching(true)
      .withStreamingSettings(StreamingSettings(successExitCodes = Set(successExitCode)))
      .ensuringCacheOutputs
    val cacheDir = CachedOutput.cacheDir(workingDir, inputProcess)
    CachedOutput.cache(cacheDir, inputProcess, exitCode)

    // actually create the files so they exist:
    workingDir.resolve(inputProcess.output.stdOutFileName.get).text = "the is std out results"
    workingDir.resolve(inputProcess.output.stdErrFileName.get).text = "the is std err results"

    val results = {
      val (_, cachedResponseOpt) = CachedOutput.cachedResponse(workingDir, HttpRequest(), inputProcess).get
      val httpResp               = cachedResponseOpt.futureValue
      inputProcess.output.streaming.get.asResult(httpResp)
    }

    results
  }
}
