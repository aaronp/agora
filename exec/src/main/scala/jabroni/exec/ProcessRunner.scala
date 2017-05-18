package jabroni.exec

import java.nio.file.Path

import akka.stream.Materializer
import jabroni.exec.log.{IterableLogger, ProcessLoggers, loggingProcessLogger}
import jabroni.rest.exchange.ExchangeClient

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.reflectiveCalls

/**
  * represents something which can be run, either locally or remotely.
  * Just adds the concept of an 'upload' to scala sys process really
  */
trait ProcessRunner {

  def run(proc: RunProcess, inputFiles: List[Upload] = Nil): ProcessRunner.ProcessOutput

  def run(cmd: String, theRest: String*): ProcessRunner.ProcessOutput = run(RunProcess(cmd :: theRest.toList, Map[String, String]()), Nil)
}

object ProcessRunner {
  type ProcessOutput = Future[Iterator[String]]

  /**
    * Creates a local runner.
    *
    * @param uploadDir              is required as a place to save uploads.
    * @param checkOutputForErrors   if true (which is should be when run locally, or false when run behind a REST
    *                               service), the output stream will be checked for the error marker
    * @param description            LocalRunners can be either shared or short-lived. In the 'short-lived' case, the
    *                               description may be e.g. a job id
    * @param workDir                the working directory to run the process under
    * @param logDir                 used by the process logger if set
    * @param errorLimit             used to truncated the std err output used when errors are encountered
    * @param includeConsoleAppender a convenience for adding a logger appender
    */
  def apply(uploadDir: Path,
            checkOutputForErrors: Boolean,
            description: String = "process",
            workDir: Option[Path] = None,
            logDir: Option[Path] = None,
            errorLimit: Option[Int] = None,
            includeConsoleAppender: Boolean = true)(implicit mat: Materializer): LocalRunner = {

    def mkLogger(proc: RunProcess): IterableLogger = {
      val iterLogger = new ProcessLoggers(description, logDir, errorLimit, proc)
      if (includeConsoleAppender) {
        iterLogger.add(loggingProcessLogger)
      }
      iterLogger
    }

    LocalRunner(uploadDir, checkOutputForErrors, workDir, mkLogger _)
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
