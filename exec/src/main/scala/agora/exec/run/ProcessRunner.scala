package agora.exec.run

import java.nio.file.Path

import agora.exec.model.{ExecuteProcess, ResultSavingRunProcessResponse, RunProcess, StreamingProcess}
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

  /**
    * Execute the [[RunProcess]], returning a future of the std out
    *
    * @param proc the job to execute
    * @return the stdout as an iterator of lines in a Future which completes when the job does
    */
  def run(proc: RunProcess): proc.Result

  /**
    * Executes the command in the provided workspace
    *
    * @param command     the command to execute
    * @param workspaceId the workspace under which the command is run
    * @return the execution response
    */
  final def execute(command: List[String], workspaceId: WorkspaceId): Future[ResultSavingRunProcessResponse] = {
    execute(ExecuteProcess(command, workspaceId))
  }

  /**
    * Executes the command in the provided workspace
    *
    * @param input the command to execute
    * @return the execution response
    */
  final def execute(input: ExecuteProcess): Future[ResultSavingRunProcessResponse] = run(input)

  /**
    * Execute the command and stream the output
    *
    * @param cmd     the command to execute
    * @param theRest the rest of the command line
    * @return a future of the iterator of results
    */
  final def stream(cmd: String, theRest: String*): Future[Iterator[String]] = {
    val input: StreamingProcess = RunProcess(cmd :: theRest.toList)
    stream(input)
  }

  /**
    * Execute the command and stream the output
    *
    * @param input the command to execute
    * @return a future of the iterator of results
    */
  final def stream(input: StreamingProcess): Future[Iterator[String]] = {
    val result: input.Result = run(input)
    result.asInstanceOf[Future[Iterator[String]]]
  }
}

object ProcessRunner {
  //type ProcessOutput = Future[Iterator[String]]

  /**
    * Creates a local runner.
    *
    * @param workDir    the working directory to run the process under
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
