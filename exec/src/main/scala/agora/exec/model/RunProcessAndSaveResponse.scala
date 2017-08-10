package agora.exec.model

import agora.api.`match`.MatchDetails
import agora.exec.workspace.WorkspaceId

case class RunProcessAndSaveResponse(exitCode: Int, workspaceId: WorkspaceId, fileName: String, matchDetails: Option[MatchDetails])
