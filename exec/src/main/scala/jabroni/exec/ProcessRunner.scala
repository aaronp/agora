package jabroni.exec

import java.nio.file.Path

import akka.stream.Materializer
import jabroni.exec.log.SplitLogger
import jabroni.rest.exchange.ExchangeClient

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls
import scala.sys.process._
import scala.util._

/**
  * prepresents something which can be run
  */
trait ProcessRunner {
  def run(proc: RunProcess, inputFiles: List[Upload] = Nil): ProcessRunner.ProcessOutput

  def run(cmd: String, theRest: String*): ProcessRunner.ProcessOutput = run(RunProcess(cmd :: theRest.toList, Map[String, String]()), Nil)
}

object ProcessRunner {
  type ProcessOutput = Future[Iterator[String]]

  def apply(uploadDir: Path,
            description: String = "process",
            workDir: Option[Path] = None,
            logDir: Option[Path] = None,
            errorLimit: Option[Int] = None,
            includeConsoleAppender: Boolean = true)(implicit mat: Materializer): LocalRunner = {
    LocalRunner(
      uploadDir = uploadDir,
      description = description,
      workDir = workDir,
      logDir = logDir,
      errorLimit = errorLimit,
      includeConsoleAppender = includeConsoleAppender)
  }

  /**
    * @param worker the worker client used to send requests
    * @return a runner which executes stuff remotely
    */
  def apply(worker: ExchangeClient,
            maximumFrameLength: Int,
            allowTruncation: Boolean)(implicit map: Materializer,
                                      uploadTimeout: FiniteDuration): ProcessRunner with AutoCloseable = {
    RemoteRunner(worker, maximumFrameLength, allowTruncation)
  }
}
