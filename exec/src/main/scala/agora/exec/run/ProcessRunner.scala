package agora.exec.run

import java.nio.file.Path

import agora.exec.model.RunProcess
import agora.exec.workspace.WorkspaceId
import agora.rest.exchange.ExchangeClient

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/**
  * represents something which can be run, either locally or remotely.
  * Just adds the concept of an 'upload' to scala sys process really
  */
trait ProcessRunner {

  def run(proc: RunProcess): ProcessRunner.ProcessOutput

  def run(cmd: String, theRest: String*): ProcessRunner.ProcessOutput = run(RunProcess(cmd :: theRest.toList, Map[String, String]()))
}

object ProcessRunner {
  type ProcessOutput = Future[Iterator[String]]

  /**
    * Creates a local runner.
    *
    * @param workDir the working directory to run the process under
    */
  def apply(workDir: Option[Path] = None)(implicit ec: ExecutionContext): LocalRunner = {
    new LocalRunner(workDir)
  }

  /**
    * @param exchange the worker client used to send requests
    * @return a runner which executes stuff remotely
    */
  def apply(exchange: ExchangeClient,
            workspaceIdOpt: Option[WorkspaceId],
            fileDependencies: Set[String],
            defaultFrameLength: Int,
            allowTruncation: Boolean,
            replaceWorkOnFailure: Boolean)(implicit uploadTimeout: FiniteDuration): ProcessRunner with AutoCloseable = {
    ExecutionClient(exchange, workspaceIdOpt, fileDependencies, defaultFrameLength, allowTruncation, replaceWorkOnFailure)
  }

}
