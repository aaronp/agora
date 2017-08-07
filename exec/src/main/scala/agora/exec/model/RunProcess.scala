package agora.exec.model

import agora.exec.workspace.{UploadDependencies, WorkspaceId}

import scala.concurrent.duration.FiniteDuration

/**
  *
  * @param command          the command string to execute
  * @param env              the system environment
  * @param successExitCodes the set of exit codes which are attribute to success
  * @param frameLength      the frame length to use (if set) for delimiting output lines
  * @param dependencies     if specified, the any file dependencies this request has
  * @param errorMarker      the marker which, if it appears in the standard output stream, will be followed by ProcessError
  *                         in json form
  */
case class RunProcess(command: List[String],
                      env: Map[String, String] = Map.empty,
                      successExitCodes: Set[Int] = Set(0),
                      frameLength: Option[Int] = None,
                      dependencies: Option[UploadDependencies] = None,
                      // when streaming results, we will already have sent a status code header (success).
                      // if we exit w/ a non-success code, then we need to indicate the start of the error response
                      errorMarker: String = RunProcess.DefaultErrorMarker) {

  def commandString                                   = command.mkString(" ")
  def withEnv(key: String, value: String): RunProcess = copy(env = env.updated(key, value))

  def withDependencies(dep: UploadDependencies): RunProcess = copy(dependencies = Option(dep))

  def withDependencies(workspace: WorkspaceId, dependsOnFiles: Set[String], timeout: FiniteDuration): RunProcess = {
    withDependencies(UploadDependencies(workspace, dependsOnFiles, timeout.toMillis))
  }

  def withWorkspace(workspace: WorkspaceId): RunProcess = {
    import concurrent.duration._
    withDependencies(workspace, Set.empty, 0.millis)
  }

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

object RunProcess {
  def apply(first: String, theRest: String*): RunProcess = new RunProcess(first :: theRest.toList)

  val DefaultErrorMarker = "*** _-={ E R R O R }=-_ ***"

  val FormDataKey = "agora.exec.model.RunProcess"
}
