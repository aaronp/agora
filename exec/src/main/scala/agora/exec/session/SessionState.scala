package agora.exec.session

private[session] case class SessionState(id: String, hasFiles: List[String], fileCount: Int) {
  def add(files: List[String]): SessionState = {
    val newFiles = (hasFiles ++ files).distinct.sorted
    this.copy(hasFiles = newFiles, fileCount = newFiles.size)
  }
}
