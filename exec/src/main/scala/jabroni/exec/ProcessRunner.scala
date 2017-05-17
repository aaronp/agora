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

  def apply(uploadDir: Path)(implicit mat: Materializer): LocalRunner = {
    apply(uploadDir, "process", Option(uploadDir), Option(uploadDir), errorLimit = None, true)
  }

  /**
    * Creates a process runner to run under the given directory
    *
    */
  def apply(uploadDir: Path,
            description: String,
            workDir: Option[Path],
            logDir: Option[Path],
            errorLimit: Option[Int],
            includeConsoleAppender: Boolean)(implicit mat: Materializer) = {
    new LocalRunner(uploadDir,
      description,
      workDir,
      logDir,
      errorLimit,
      includeConsoleAppender)
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


  /**
    * The function which actually executes stuff
    */
  def runUnsafe(proc: RunProcess,
                workDir: Option[java.io.File],
                log: SplitLogger)(implicit ec: ExecutionContext): Future[Int] = {

    val startedTry: Try[Process] = Try {
      Process(proc.command, workDir, proc.env.toSeq: _*).run(log)
    }
    startedTry match {
      case Success(process) =>
        val future = Future(process.exitValue())
        future.onComplete { _ =>
          log.flush()
          log.close()
        }
        future
      case Failure(err) =>
        log.flush()
        log.close()
        Future.failed(err)
    }
  }

}
