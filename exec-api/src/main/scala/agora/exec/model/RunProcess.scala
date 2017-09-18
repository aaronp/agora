package agora.exec.model

import agora.exec.workspace.{UploadDependencies, WorkspaceId}

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

  def fileOutputs = output.stdOutFileName.toList ::: output.stdErrFileName.toList

  def hasFileOutputs: Boolean = fileOutputs.nonEmpty

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

  def withStreamingSettings(settings: StreamingSettings): RunProcess = copy(output = output.withSettings(settings))

  def withoutStreaming(): RunProcess = copy(output = output.copy(stream = None))

  def withDependencies(dep: UploadDependencies) = copy(dependencies = dep)

  def withDependencies(dependsOnFiles: Set[String], timeout: FiniteDuration = dependencies.timeout): RunProcess = {
    withDependencies(UploadDependencies(workspace, dependsOnFiles, timeout.toMillis))
  }

  def withDependencies(newWorkspace: WorkspaceId, dependsOnFiles: Set[String], timeout: FiniteDuration): RunProcess = {
    withDependencies(UploadDependencies(newWorkspace, dependsOnFiles, timeout.toMillis))
  }

  def withWorkspace(newWorkspace: WorkspaceId): RunProcess = {
    import concurrent.duration._
    withDependencies(dependencies.copy(workspace = newWorkspace))
  }

}

object RunProcess {

  def apply(first: String, theRest: String*): RunProcess = apply(first :: theRest.toList)

  val FormDataKey = "agora.exec.model.RunProcess"
}
