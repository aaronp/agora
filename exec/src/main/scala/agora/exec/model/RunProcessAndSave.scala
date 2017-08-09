package agora.exec.model

import agora.exec.workspace.WorkspaceId

case class RunProcessAndSave(process : RunProcess, workspaceId: WorkspaceId, stdOutFileName : String, stdErrFileName : String)
