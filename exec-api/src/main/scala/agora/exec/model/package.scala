package agora.exec

import java.util.UUID

import agora.exec.workspace.WorkspaceId

package object model {

  def newWorkspace(): WorkspaceId = UUID.randomUUID().toString
}
