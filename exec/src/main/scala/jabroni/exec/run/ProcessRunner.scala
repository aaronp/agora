package jabroni.exec.run

import java.nio.file.Path

import akka.stream.Materializer
import jabroni.exec.dao.UploadDao
import jabroni.exec.log.IterableLogger
import jabroni.exec.model.{RunProcess, Upload}
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

  def local(uploadDao: UploadDao = UploadDao(),
            workDir: Option[Path] = None,
            loggerForProcess: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit mat: Materializer): LocalRunner = {
    new LocalRunner(uploadDao, workDir, loggerForProcess) {
      override def execute(builder: process.ProcessBuilder, proc: RunProcess, iterableLogger: IterableLogger): Iterator[String] = {
        proc.filterForErrors(super.execute(builder, proc, iterableLogger))
      }

      override def withWorkDir(wd: Option[Path]): LocalRunner = local(uploadDao, wd, loggerForProcess)

      override def withUploadDao(dao: UploadDao): LocalRunner = local(dao, workDir, loggerForProcess)

      override def withLogger(newLoggerForProcess: RunProcess => IterableLogger) = local(uploadDao, workDir, newLoggerForProcess)
    }
  }

  /**
    * Creates a local runner.
    *
    * @param workDir the working directory to run the process under
    */
  def apply(uploadDao: UploadDao,
            workDir: Option[Path] = None,
            loggerForJob: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit mat: Materializer): LocalRunner = {
    LocalRunner(uploadDao, workDir, loggerForJob)
  }

  /**
    * @param exchange the worker client used to send requests
    * @return a runner which executes stuff remotely
    */
  def apply(exchange: ExchangeClient,
            defaultFrameLength: Int,
            allowTruncation: Boolean,
            replaceWorkOnFailure: Boolean)(implicit map: Materializer,
                                           uploadTimeout: FiniteDuration): ProcessRunner with AutoCloseable = {
    RemoteRunner(exchange, defaultFrameLength, allowTruncation, replaceWorkOnFailure)
  }


}
