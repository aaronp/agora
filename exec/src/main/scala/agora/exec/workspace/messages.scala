package agora.exec.workspace

import java.nio.file.Path

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Promise

private[workspace] sealed trait Msg

private[workspace] final case class ListWorkspaces(result: Promise[List[String]]) extends Msg

private[workspace] final case class UploadFile(workspaceId: WorkspaceId, name: String, src: Source[ByteString, Any], result: Promise[Boolean]) extends Msg

private[workspace] final case class Close(workspaceId: WorkspaceId, result: Promise[Boolean]) extends Msg

private[workspace] final case class AwaitUploads(workspace: WorkspaceId, fileDependencies: Set[String], workDirResult: Promise[Path]) extends Msg
