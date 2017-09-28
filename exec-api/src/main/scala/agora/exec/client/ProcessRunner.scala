package agora.exec.client

import java.nio.file.Path

import agora.api.exchange.Exchange
import agora.exec.ExecApiConfig
import agora.exec.model._
import agora.exec.workspace.WorkspaceId
import akka.stream.Materializer

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
  def run(proc: RunProcess): Future[RunProcessResult]

  /**
    * Executes the command in the provided workspace
    *
    * @param command     the command to execute
    * @param workspaceId the workspace under which the command is run
    * @return the execution response
    */
  final def run(command: List[String], workspaceId: WorkspaceId): Future[RunProcessResult] = {
    run(RunProcess(command).withWorkspace(workspaceId))
  }

  /**
    * Executes the command in the provided workspace
    *
    * @param command the command to execute
    * @param args    the command arguments
    * @return the execution response
    */
  final def run(command: String, args: String*): Future[RunProcessResult] = {
    run(command :: args.toList, agora.exec.model.newWorkspace())
  }

  final def save(proc: RunProcess): Future[FileResult] = run(proc.withoutStreaming()).mapTo[FileResult]

  final def stream(proc: RunProcess): Future[StreamingResult] = {
    val future = proc.output.streaming match {
      case None    => run(proc.withStreamingSettings(StreamingSettings()))
      case Some(_) => run(proc)
    }
    future.mapTo[StreamingResult]
  }

  final def stream(command: String, args: String*): Future[StreamingResult] =
    stream(RunProcess(command :: args.toList))

}

object ProcessRunner {

  /**
    * Creates a local runner.
    *
    * @param workDir    the working directory to run the process under
    * @param defaultEnv environment variables to be made available to all processes run
    */
  def apply(workDir: Option[Path] = None, defaultEnv: Map[String, String] = Map.empty)(
      implicit ec: ExecutionContext) = {
    LocalRunner(workDir).withDefaultEnv(defaultEnv)
  }

  def local(workPath: String)(implicit ec: ExecutionContext): LocalRunner = {
    import agora.io.implicits._
    LocalRunner(Option(workPath).map(_.asPath))
  }

  /**
    * @param exchange the worker client used to send requests
    * @return a runner which executes stuff remotely
    */
  def apply(exchange: Exchange, execApiConfig: ExecApiConfig)(implicit mat: Materializer) = {
    new RemoteRunner(exchange, execApiConfig)
  }

}
