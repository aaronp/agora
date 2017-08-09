package agora.exec.run

import java.nio.file.{Path, Paths}

import agora.exec.model.{RunProcess, RunProcessAndSave, RunProcessAndSaveResponse}
import agora.exec.workspace.{UploadDependencies, WorkspaceId}
import agora.rest.exchange.ExchangeClient

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/**
  * represents something which can be run, either locally or remotely.
  * Just adds the concept of an 'upload' to scala sys process really
  */
trait ProcessRunner {

  /**
    * Execute the [[RunProcess]], returning a future of the std out
    * @param proc the job to execute
    * @return the stdout as an iterator of lines in a Future which completes when the job does
    */
  def run(proc: RunProcess): ProcessRunner.ProcessOutput

  /**
    * Execute the [[RunProcessAndSave]], writing the results to disk
    *
    * @param proc
    * @return a future of the response
    */
  def runAndSave(proc: RunProcessAndSave): Future[RunProcessAndSaveResponse]

  final def run(cmd: String, theRest: String*): ProcessRunner.ProcessOutput = {
    run(RunProcess(cmd :: theRest.toList, Map[String, String]()))
  }
}

object ProcessRunner {
  type ProcessOutput = Future[Iterator[String]]

  /**
    * Creates a local runner.
    *
    * @param workDir the working directory to run the process under
    * @param defaultEnv environment variables to be made available to all processes run
    */
  def apply(workDir: Option[Path] = None, defaultEnv: Map[String, String] = Map.empty)(implicit ec: ExecutionContext): LocalRunner = {
    new LocalRunner(workDir, defaultEnv)
  }

  def local(workPath: String)(implicit ec: ExecutionContext): LocalRunner = {
    import agora.io.implicits._
    apply(Option(workPath).map(_.asPath))
  }

  /**
    * @param exchange the worker client used to send requests
    * @return a runner which executes stuff remotely
    */
  def apply(exchange: ExchangeClient, defaultFrameLength: Int, allowTruncation: Boolean, replaceWorkOnFailure: Boolean)(implicit uploadTimeout: FiniteDuration) = {
    RemoteRunner(exchange, defaultFrameLength, allowTruncation, replaceWorkOnFailure)
  }

}
