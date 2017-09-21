package agora.exec.workspace

import java.nio.file.Path

import agora.api.json.AgoraJsonImplicits$
import agora.api.time._
import agora.io.dao.{IdDao, TimestampDao}
import agora.io.implicits._
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.ExecutionContext


/**
  * A [[WorkspaceClient]] which keeps track of new workspaces and when they're created, so that we can reep them
  * (gobble them up), after they are older than a given window
  *
  * @param dir
  * @param underlying
  */
class TrackingWorkspaceClient(dir: Path,
                              underlying: WorkspaceClient,
                             )(implicit ec: ExecutionContext) extends WorkspaceClientDelegate(underlying) with AgoraJsonImplicits$ {
  import TrackingWorkspaceClient._

  private val idDao = IdDao[Timestamp](dir)
  private val absoluteWorkspacePathsDao = TimestampDao[String](dir)

  /**
    * @param timestamp the time before which workspaces are removed
    * @return the workspaces
    */
  def removeWorkspacesBefore(timestamp: Timestamp) = {
    val removed: Option[Iterator[WorkspaceId]] = absoluteWorkspacePathsDao.first.filter(_.isBefore(timestamp)).map { oldest =>
      val workspaces: Iterator[String] = absoluteWorkspacePathsDao.find(oldest, timestamp)
      workspaces.filter { absoluteWorkspacePath =>
        val workspace: Path = absoluteWorkspacePath.asPath
        if (allFilesAreOlderThanTime(workspace, timestamp)) {
          idDao.remove(workspace.fileName)
          true
        } else {
          false
        }
      }
    }
    val removedList = removed.toList.flatten
    absoluteWorkspacePathsDao.removeBefore(timestamp)
    removedList
  }

  override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]) = {
    val savedFileFuture = underlying.upload(workspaceId, fileName, src)
    savedFileFuture.onSuccess {
      case uploadedFile =>

        idDao.synchronized {
          if (!idDao.contains(workspaceId)) {
            val created = now()
            idDao.save(workspaceId, created)
            absoluteWorkspacePathsDao.save(uploadedFile.getParent.toAbsolutePath.toString, created)
          }
        }
    }
    savedFileFuture
  }
}


object TrackingWorkspaceClient {

  def allFilesAreOlderThanTime(workspace: Path, timestamp: Timestamp): Boolean = {

    val files: Iterator[Path] = workspace.nestedFiles()

    files.forall(_.lastModifiedLocalDateTime.isAfter(timestamp))

  }
}