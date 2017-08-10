package agora.exec.model

import agora.exec.workspace.{UploadDependencies, WorkspaceId}

case class RunProcessAndSave(command: List[String],
                             workspaceId: WorkspaceId,
                             stdOutFileName: String = "std.out",
                             stdErrFileName: String = "std.err",
                             dependencyTimeoutInMillis: Long = 0,
                             fileDependencies: Set[String] = Set.empty,
                             env: Map[String, String] = Map.empty,
                             successExitCodes: Set[Int] = Set(0)) {
  def uploadDependencies = UploadDependencies(workspaceId, fileDependencies, dependencyTimeoutInMillis)

  def asRunProcess = RunProcess(command, env, successExitCodes, dependencies = Option(uploadDependencies))
}
