package agora.exec.workspace

import agora.exec.workspace.DependencyCheck.DependencyState

/**
  * TODO - catch/propagate this in the execution routes
  */
class WorkspaceDependencyTimeoutException(dependencies: UploadDependencies, dependencyStates: Map[String, DependencyState], override val getMessage: String)
    extends Exception(getMessage) {
  override def toString = getMessage
}

object WorkspaceDependencyTimeoutException {
  def apply(dependencies: UploadDependencies, dependencyStates: Map[String, DependencyState]) = {
    val errMsg =
      s"Workspace '${dependencies.workspace}' timed out after ${dependencies.timeout} with dependency states: ${dependencyStates.toList.sortBy(_._1).mkString(",")}"
    new WorkspaceDependencyTimeoutException(dependencies, dependencyStates, errMsg)
  }

}
