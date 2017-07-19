package agora.exec.session

import java.nio.file.Path

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Promise

private[session] sealed trait Msg

private[session] final case class ListSessions(result: Promise[List[String]]) extends Msg

private[session] final case class UploadFile(sessionId: SessionId, name: String, src: Source[ByteString, Any], result: Promise[Boolean]) extends Msg

private[session] final case class Close(sessionId: SessionId, result: Promise[Boolean]) extends Msg

private[session] final case class AwaitUploads(session: SessionId, fileDependencies: Set[String], workDirResult: Promise[Path]) extends Msg
