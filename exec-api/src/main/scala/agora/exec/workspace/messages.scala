package agora.exec.workspace

import java.nio.file.Path

import agora.api.time.Timestamp
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Promise

/**
  * Workspace messages are sent to actors specific to a workspace: [[WorkspaceActor]]
  *
  * they are all routed through the owning [[WorkspaceEndpointActor]]
  */
sealed trait WorkspaceMsg {
  def workspaceId: WorkspaceId
}

private[workspace] final case class ListWorkspaces(createdAfter: Option[Timestamp] = None,
                                                   createdBefore: Option[Timestamp] = None,
                                                   result: Promise[List[String]])

private[workspace] final case class UploadFile(override val workspaceId: WorkspaceId,
                                               name: String,
                                               src: Source[ByteString, Any],
                                               result: Promise[Path])
    extends WorkspaceMsg

private[workspace] final case class TriggerUploadCheck(override val workspaceId: WorkspaceId) extends WorkspaceMsg

private[workspace] final case class Close(override val workspaceId: WorkspaceId,
                                          ifNotModifiedSince: Option[Timestamp],
                                          failPendingDependencies: Boolean,
                                          result: Promise[Boolean])
    extends WorkspaceMsg

private[workspace] final case class AwaitUploads(dependencies: UploadDependencies, workDirResult: Promise[Path])
    extends WorkspaceMsg {
  override def workspaceId = dependencies.workspace
}
