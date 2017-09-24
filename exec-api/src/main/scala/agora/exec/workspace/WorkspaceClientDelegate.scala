package agora.exec.workspace
import agora.io.dao.Timestamp
import akka.stream.scaladsl.Source
import akka.util.ByteString

/**
  * trait for helping subclasses of WorkspaceClient just override the functions of interest
  */
trait WorkspaceClientDelegate extends WorkspaceClient {

  protected def underlying: WorkspaceClient

  override def close(workspaceId: WorkspaceId, ifNotModifiedSince: Option[Timestamp], failPendingDependencies: Boolean) = {
    underlying.close(workspaceId, ifNotModifiedSince, failPendingDependencies)
  }

  override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]) = {
    underlying.upload(workspaceId, fileName, src)
  }

  override def triggerUploadCheck(workspaceId: WorkspaceId) = {
    underlying.triggerUploadCheck(workspaceId)
  }

  override def await(dependencies: UploadDependencies) = {
    underlying.await(dependencies)
  }

  override def list(createdAfter: Option[Timestamp], createdBefore: Option[Timestamp]) = {
    underlying.list(createdAfter, createdBefore)
  }
}
