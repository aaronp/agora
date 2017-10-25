package agora.exec.workspace

/**
  * TODO - catch/propogate this in the execution routes
  *
  * @param dependencies
  * @param missing
  * @param getMessage
  */
class WorkspaceDependencyTimeoutException(dependencies: UploadDependencies, missing: Set[String], override val getMessage: String)
    extends Exception(getMessage) {
  override def toString = getMessage
}

object WorkspaceDependencyTimeoutException {
  def apply(dependencies: UploadDependencies) = {
    val errMsg = s"No files have been uploaded to ${dependencies.workspace} after ${dependencies.timeout}"
    new WorkspaceDependencyTimeoutException(dependencies, dependencies.dependsOnFiles, errMsg)
  }

  def apply(dependencies: UploadDependencies, missing: Set[String]) = {
    val errMsg =
      s"Still waiting for ${missing.size} files [${missing.mkString(",")}] in workspace '${dependencies.workspace}' after ${dependencies.timeout}"

    new WorkspaceDependencyTimeoutException(dependencies, missing, errMsg)
  }
}
