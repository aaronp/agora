package agora.exec.workspace
import java.nio.file.Path

import agora.io.dao.Timestamp
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

/**
  * trait for helping subclasses of WorkspaceClient just override the functions of interest
  */
trait WorkspaceClientDelegate extends WorkspaceClient {

  protected def underlying: WorkspaceClient

  override def close(): Unit = underlying.close()

  override def close(workspaceId: WorkspaceId, ifNotModifiedSince: Option[Timestamp], failPendingDependencies: Boolean) = {
    underlying.close(workspaceId, ifNotModifiedSince, failPendingDependencies)
  }

  override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]): Future[(Long, Path)] = {
    underlying.upload(workspaceId, fileName, src)
  }

  override def triggerUploadCheck(workspaceId: WorkspaceId) = {
    underlying.triggerUploadCheck(workspaceId)
  }

  override def markComplete(workspaceId: WorkspaceId, fileSizeByFileWritten: Map[String, Long]) = {
    underlying.markComplete(workspaceId, fileSizeByFileWritten)
  }

  override def awaitWorkspace(dependencies: UploadDependencies) = {
    underlying.awaitWorkspace(dependencies)
  }

  override def list(createdAfter: Option[Timestamp], createdBefore: Option[Timestamp]) = {
    underlying.list(createdAfter, createdBefore)
  }
}
