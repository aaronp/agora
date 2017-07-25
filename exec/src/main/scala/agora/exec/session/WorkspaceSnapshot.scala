package agora.exec.session

private[session] case class WorkspaceSnapshot(sessionId: SessionId, hasFiles: List[String], fileCount: Int) {
  def add(files: List[String]): WorkspaceSnapshot = {
    val newFiles = (hasFiles ++ files).distinct.sorted
    this.copy(hasFiles = newFiles, fileCount = newFiles.size)
  }
}
