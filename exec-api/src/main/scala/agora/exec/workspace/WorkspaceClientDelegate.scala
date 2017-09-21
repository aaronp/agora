package agora.exec.workspace
import akka.stream.scaladsl.Source
import akka.util.ByteString

/**
  * Delegate for easily overriding single methods
  * @param underlying
  */
abstract class WorkspaceClientDelegate(underlying : WorkspaceClient) extends WorkspaceClient {

  override def close(workspaceId: WorkspaceId) = {
    underlying.close(workspaceId)
  }

  override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]) = {
    underlying.upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any])
  }

  override def triggerUploadCheck(workspaceId: WorkspaceId) = {
    underlying.triggerUploadCheck(workspaceId)
  }

  override def await(dependencies: UploadDependencies) = {
    underlying.await(dependencies)
  }

  override def list() = {
    underlying.list()
  }
}
