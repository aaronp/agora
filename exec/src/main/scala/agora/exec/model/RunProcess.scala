package agora.exec.model

import agora.exec.workspace.{UploadDependencies, WorkspaceId}
import io.circe.Decoder.Result

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Represents a command to execute a process. There are two sealed implementations:
  * [[StreamingProcess]] and [[ExecuteProcess]].
  */
sealed trait RunProcess {

  def command: List[String]

  def env: Map[String, String]

  def withCommand(newCommand: List[String]): Self

  /** @return a new 'RunProcess' with the command line having replaced '$<key>' with '<value>' from the environment
    */
  def resolveEnv = {
    val newCommand = env.foldLeft(command) {
      case (commandLine, (k, value)) =>
        val regex = "\\$" + k + "\\b"
        commandLine.map(_.replaceAll(regex, value))
    }
    withCommand(newCommand)
  }

  type Result
  type Self <: RunProcess
}

object RunProcess {

  import io.circe._
  import io.circe.generic.auto._

  private case class ExecuteProcessJsonData(command: List[String],
                                            workspaceId: WorkspaceId,
                                            stdOutFileName: String,
                                            stdErrFileName: String,
                                            dependencyTimeoutInMillis: Long,
                                            fileDependencies: Set[String],
                                            env: Map[String, String],
                                            successExitCodes: Set[Int] = Set(0)) {
    def asRunProcess: ExecuteProcess = {
      //      val genA = Generic[ResultSavingRunProcess]
      //      val genB = Generic[ExecuteProcessJsonData]
      //      genA.from(genB.to(this))
      ExecuteProcess(command, workspaceId, stdOutFileName, stdErrFileName, dependencyTimeoutInMillis, fileDependencies, env)
    }

  }

  private object ExecuteProcessJsonData {
    def apply(from: ExecuteProcess): ExecuteProcessJsonData = {
      import from._
      ExecuteProcessJsonData(command, workspaceId, stdOutFileName, stdErrFileName, dependencyTimeoutInMillis, fileDependencies, env)
    }
  }

  private case class StreamingProcessJsonData(command: List[String], env: Map[String, String], streamingSettings: StreamingSettings) {
    def asRunProcess: StreamingProcess = {
      StreamingProcess(command, env, streamingSettings)
    }
  }

  private object StreamingProcessJsonData {
    def apply(from: StreamingProcess): StreamingProcessJsonData = {
      //      val genA = Generic[StreamingOutputRunProcess]
      //      val genB = Generic[StreamingProcessJsonData]
      //      genB.from(genA.to(from))
      import from._
      StreamingProcessJsonData(command, env, streamingSettings)
    }
  }

  def apply(first: String, theRest: String*): StreamingProcess = apply(first :: theRest.toList)

  def apply(command: List[String],
            env: Map[String, String] = Map.empty,
            successExitCodes: Set[Int] = Set(0),
            frameLength: Option[Int] = None,
            dependencies: Option[UploadDependencies] = None,
            // when streaming results, we will already have sent a status code header (success).
            // if we exit w/ a non-success code, then we need to indicate the start of the error response
            errorMarker: String = RunProcess.DefaultErrorMarker): StreamingProcess = {
    val settings = StreamingSettings(successExitCodes, frameLength, dependencies, errorMarker)
    new StreamingProcess(command, env, settings)
  }

  implicit object RunProcessFormat extends Encoder[RunProcess] with Decoder[RunProcess] {

    import io.circe.syntax._

    override def apply(input: RunProcess): Json = {
      input match {
        case value: ExecuteProcess   => ExecuteProcessJsonData(value).asJson
        case value: StreamingProcess => StreamingProcessJsonData(value).asJson
      }
    }

    override def apply(c: HCursor): Result[RunProcess] = {
      import cats.syntax.either._
      c.as[ExecuteProcessJsonData].map(_.asRunProcess).orElse(c.as[StreamingProcessJsonData].map(_.asRunProcess))

    }
  }

  val DefaultErrorMarker = "*** _-={ E R R O R }=-_ ***"

  val FormDataKey = "agora.exec.model.RunProcess"
}

import agora.exec.workspace.{UploadDependencies, WorkspaceId}

/**
  * An implementation of [[RunProcess]] which writes its results to a workspace (e.g. session) directory
  *
  * Subsequent jobs run in the same workspace will have access to the std out/std err produced by this job
  *
  * @param command
  * @param workspaceId
  * @param stdOutFileName
  * @param stdErrFileName
  * @param dependencyTimeoutInMillis
  * @param fileDependencies
  * @param env
  * @param successExitCodes
  */
case class ExecuteProcess(override val command: List[String],
                          workspaceId: WorkspaceId,
                          stdOutFileName: String = "std.out",
                          stdErrFileName: String = "std.err",
                          dependencyTimeoutInMillis: Long = 0,
                          fileDependencies: Set[String] = Set.empty,
                          override val env: Map[String, String] = Map.empty,
                          successExitCodes: Set[Int] = Set(0))
    extends RunProcess {
  override type Self   = ExecuteProcess
  override type Result = Future[ResultSavingRunProcessResponse]

  override def withCommand(newCommand: List[String]) = copy(command = newCommand)

  def uploadDependencies = UploadDependencies(workspaceId, fileDependencies, dependencyTimeoutInMillis)

  def asStreamingProcess: StreamingProcess = RunProcess(command, env, successExitCodes, dependencies = Option(uploadDependencies))

}

/**
  * A job indended to produce a lot of output, and such streams the output when run.
  *
  * @param command the command string to execute
  * @param env     the system environment
  */
case class StreamingProcess(override val command: List[String], override val env: Map[String, String] = Map.empty, streamingSettings: StreamingSettings = StreamingSettings())
    extends RunProcess {
  def isSuccessfulExitCode(code: Int) = {
    streamingSettings.successExitCodes.contains(code)
  }

  def dependencies = streamingSettings.dependencies

  def frameLength = streamingSettings.frameLength

  override type Result = Future[Iterator[String]]
  override type Self   = StreamingProcess

  override def withCommand(newCommand: List[String]) = copy(command = newCommand)

  def commandString = command.mkString(" ")

  def withEnv(newEnv: Map[String, String]) = copy(env = newEnv)
  def withEnv(key: String, value: String)  = copy(env = env.updated(key, value))

  def withSettings(settings: StreamingSettings): StreamingProcess = copy(streamingSettings = settings)

  def withDependencies(dep: UploadDependencies): StreamingProcess = withSettings(streamingSettings.withDependencies(dep))

  def withDependencies(workspace: WorkspaceId, dependsOnFiles: Set[String], timeout: FiniteDuration): StreamingProcess = {
    withDependencies(UploadDependencies(workspace, dependsOnFiles, timeout.toMillis))
  }

  def withWorkspace(workspace: WorkspaceId): RunProcess = {
    import concurrent.duration._
    withDependencies(workspace, Set.empty, 0.millis)
  }

  def errorMarker = streamingSettings.errorMarker

  /**
    * Filters the iterator produced using this RunProcess for errors
    *
    * @param lineIter
    * @return
    */
  def filterForErrors(lineIter: Iterator[String]): Iterator[String] = {
    lineIter.map {
      case line if line == errorMarker =>
        val json = lineIter.mkString("\n")
        ProcessError.fromJsonString(json) match {
          case Right(processError) => throw new ProcessException(processError)
          case Left(parseError) =>
            sys.error(s"Encountered the error marker '${errorMarker}', but couldn't parse '$json' : $parseError")
        }
      case ok => ok
    }
  }
}

/**
  * @param successExitCodes the set of exit codes which are attribute to success
  * @param frameLength      the frame length to use (if set) for delimiting output lines
  * @param dependencies     if specified, the any file dependencies this request has
  * @param errorMarker      the marker which, if it appears in the standard output stream, will be followed by ProcessError
  *                         in json form
  */
case class StreamingSettings(successExitCodes: Set[Int] = Set(0),
                             frameLength: Option[Int] = None,
                             dependencies: Option[UploadDependencies] = None,
                             // when streaming results, we will already have sent a status code header (success).
                             // if we exit w/ a non-success code, then we need to indicate the start of the error response
                             errorMarker: String = RunProcess.DefaultErrorMarker) {
  def withDependencies(dep: UploadDependencies) = copy(dependencies = Option(dep))
}
