package jabroni.exec

import java.nio.file.Path

import akka.stream.Materializer
import jabroni.exec.log.{IterableLogger, ProcessLoggers, loggingProcessLogger}
import jabroni.rest.exchange.ExchangeClient

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.reflectiveCalls

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

    def mkLogger(proc: RunProcess): IterableLogger = {
      val iterLogger = new ProcessLoggers(description, logDir, errorLimit, proc)
      if (includeConsoleAppender) {
        iterLogger.add(loggingProcessLogger)
      }
      iterLogger
    }

    LocalRunner(uploadDir, workDir, mkLogger _)
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
