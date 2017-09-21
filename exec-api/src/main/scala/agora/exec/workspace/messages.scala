package agora.exec.workspace

import java.nio.file.Path

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Promise

sealed trait WorkspaceMsg {
  def workspaceId: WorkspaceId
}

private[workspace] final case class ListWorkspaces(result: Promise[List[String]])

private[workspace] final case class UploadFile(override val workspaceId: WorkspaceId, name: String, src: Source[ByteString, Any], result: Promise[Path]) extends WorkspaceMsg

private[workspace] final case class TriggerUploadCheck(override val workspaceId: WorkspaceId) extends WorkspaceMsg

private[workspace] final case class Close(override val workspaceId: WorkspaceId, result: Promise[Boolean]) extends WorkspaceMsg

private[workspace] final case class AwaitUploads(dependencies: UploadDependencies, workDirResult: Promise[Path]) extends WorkspaceMsg {
  override def workspaceId = dependencies.workspace
}
