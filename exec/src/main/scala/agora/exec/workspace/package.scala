package agora.exec

package object workspace {
  type WorkspaceId = String

  def safeId(workspaceId: WorkspaceId) = workspaceId.filter(_.isLetterOrDigit)
}
