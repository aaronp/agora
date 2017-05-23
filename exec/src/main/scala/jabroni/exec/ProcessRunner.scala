package jabroni.exec

import java.nio.file.Path

import akka.stream.Materializer
import jabroni.exec.log.{IterableLogger, ProcessLoggers, loggingProcessLogger}
import jabroni.rest.exchange.ExchangeClient

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.reflectiveCalls
import scala.sys.process

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

  def local(uploadDir: Path,
            workDir: Option[Path] = None,
            loggerForProcess: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit mat: Materializer): LocalRunner = {
    new LocalRunner(uploadDir, workDir, loggerForProcess) {
      override def execute(builder: process.ProcessBuilder, proc: RunProcess, iterableLogger: IterableLogger): Iterator[String] = {
        proc.filterForErrors(super.execute(builder, proc, iterableLogger))
      }

      override def withUploadDir(up: Path): LocalRunner = local(up, workDir, loggerForProcess)

      override def withWorkDir(wd: Option[Path]): LocalRunner = local(uploadDir, wd, loggerForProcess)

      override def withLogger(newLoggerForProcess: RunProcess => IterableLogger) = local(uploadDir, workDir, newLoggerForProcess)
    }
  }

  /**
    * Creates a local runner.
    *
    * @param uploadDir is required as a place to save uploads.
    * @param workDir   the working directory to run the process under
    */
  def apply(uploadDir: Path,
            workDir: Option[Path] = None,
            loggerForJob: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit mat: Materializer): LocalRunner = {
    LocalRunner(uploadDir, workDir, loggerForJob)
  }

  /**
    * @param exchange the worker client used to send requests
    * @return a runner which executes stuff remotely
    */
  def apply(exchange: ExchangeClient,
            defaultFrameLength: Int,
            allowTruncation: Boolean)(implicit map: Materializer,
                                      uploadTimeout: FiniteDuration): ProcessRunner with AutoCloseable = {
    RemoteRunner(exchange, defaultFrameLength, allowTruncation)
  }


}
