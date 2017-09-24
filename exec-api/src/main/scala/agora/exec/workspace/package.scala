package agora.exec

import java.nio.file.Path
import java.time.ZoneOffset

import agora.api.time.Timestamp

package object workspace {
  type WorkspaceId = String

  def safeId(workspaceId: WorkspaceId) = workspaceId.filter(_.isLetterOrDigit)

  /** @param directory the directory to check
    * @param timestamp
    * @return
    */
  def allFilesAreOlderThanTime(directory: Path, timestamp: Timestamp): Boolean = {
    import agora.io.implicits._
    val epoch = timestamp.toEpochSecond(ZoneOffset.UTC) * 1000
    val files = directory.nestedFiles()
    if (!files.hasNext) {
      true
    } else {
      val mostRecentlyModified: Path = files.maxBy { file =>
        file.lastModifiedMillis
      }
      val canRemove = mostRecentlyModified.lastModifiedMillis < epoch

      canRemove
    }
  }
}
