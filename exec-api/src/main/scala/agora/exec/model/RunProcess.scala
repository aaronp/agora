package agora.exec.model

import java.util.UUID

import agora.exec.workspace.{UploadDependencies, WorkspaceId}
import agora.io.MD5

import scala.concurrent.duration.FiniteDuration

/**
  * A job intended to produce a lot of output, and such streams the output when run.
  *
  * @param dependencies if specified, the any file dependencies this request has
  * @param command      the command string to execute
  * @param env          the system environment
  */
case class RunProcess(command: List[String],
                      env: Map[String, String] = Map.empty,
                      dependencies: UploadDependencies = UploadDependencies(newWorkspace(), Set.empty, 0),
                      output: OutputSettings = OutputSettings()) {

  /**
    * If [[OutputSettings.canCache]] is set, then we ensure that stdout and stderr are set to unique values if left unset
    *
    * @return a RunProcess which ensures the stdout and stderr filenames are set
    */
  def ensuringCacheOutputs = {
    if (!output.canCache) {
      this
    } else {
      def uniqueName = UUID.randomUUID().toString

      (output.stdOutFileName, output.stdErrFileName) match {
        case (Some(_), Some(_)) => this
        case (None, Some(_))    => withOutput(output.copy(stdOutFileName = Option(uniqueName)))
        case (Some(_), None)    => withOutput(output.copy(stdErrFileName = Option(uniqueName)))
        case (None, None) =>
          val newOutput = output.copy(stdOutFileName = Option(uniqueName), stdErrFileName = Option(uniqueName))
          withOutput(newOutput)
      }
    }
  }

  /** @return the MD5 hash of the command string as a Hex string
    */
  def commandHash: String = MD5(commandString)

  def fileOutputs = output.stdOutFileName.toList ::: output.stdErrFileName.toList

  def hasFileOutputs: Boolean = fileOutputs.nonEmpty

  def hasDependencies = dependencies.dependsOnFiles.nonEmpty

  def withStdOutTo(fileName: String): RunProcess = copy(output = output.copy(stdOutFileName = Option(fileName)))

  def workspace: WorkspaceId = dependencies.workspace

  /** @return a new 'RunProcess' with the command line having replaced '$<key>' with '<value>' from the envi
    */
  def resolveEnv = {
    val newCommand = env.foldLeft(command) {
      case (commandLine, (k, value)) =>
        val regex = "\\$" + k + "\\b"
        commandLine.map(_.replaceAll(regex, value))
    }
    withCommand(newCommand)
  }

  def withCommand(newCommand: List[String]) = copy(command = newCommand)

  def commandString = command.mkString(" ")

  def withEnv(newEnv: Map[String, String]) = copy(env = newEnv)

  def withEnv(key: String, value: String) = copy(env = env.updated(key, value))

  def withStreamingSettings(settings: StreamingSettings): RunProcess = withOutput(output.withSettings(settings))

  def withoutStreaming(): RunProcess = withOutput(output.copy(streaming = None))

  def withOutput(newOutput: OutputSettings): RunProcess = copy(output = newOutput)

  def withCaching(cache: Boolean): RunProcess = withOutput(output.copy(canCache = cache))

  def useCachedValueWhenAvailable(cache: Boolean): RunProcess = {
    withOutput(output.copy(useCachedValueWhenAvailable = cache))
  }

  def withDependencies(dep: UploadDependencies) = copy(dependencies = dep)

  def withDependencies(dependsOnFiles: Set[String], timeout: FiniteDuration = dependencies.timeout): RunProcess = {
    withDependencies(UploadDependencies(workspace, dependsOnFiles, timeout.toMillis))
  }

  def withDependencies(newWorkspace: WorkspaceId, dependsOnFiles: Set[String], timeout: FiniteDuration): RunProcess = {
    withDependencies(UploadDependencies(newWorkspace, dependsOnFiles, timeout.toMillis))
  }

  def withWorkspace(newWorkspace: WorkspaceId): RunProcess = {
    withDependencies(dependencies.copy(workspace = newWorkspace))
  }

}

object RunProcess {

  def apply(first: String, theRest: String*): RunProcess = apply(first :: theRest.toList)

  val FormDataKey = "agora.exec.model.RunProcess"
}
