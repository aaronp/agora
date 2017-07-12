package agora.exec.session

case class SessionState(id: String, hasFiles: List[String], fileCount: Int) {
  def add(files: List[String]) = {
    val newFiles = (hasFiles ++ files).distinct.sorted
    copy(hasFiles = newFiles, fileCount = newFiles.size)
  }

}
