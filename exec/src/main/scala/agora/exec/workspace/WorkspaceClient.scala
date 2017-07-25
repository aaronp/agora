package agora.exec.workspace

import java.nio.file.Path

import agora.exec.model.Upload
import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  * Represents something which can manage a 'workspace'.
  * The 'workspace' can be thought of as a workspace in terms of a single user's interactions
  * with a the file system.
  *
  * They may upload files, execute commands, etc within a working directory (the workspace).
  *
  * They can also 'await' files to become available in that workspace.
  */
trait WorkspaceClient {

  /**
    * closing the workspace releases the resources used -- e.g. deletes the relevant directory
    * @param workspaceId
    * @return a future which completes once the workspace has been cleaned up, with the boolean value signalling if it was a known/valid workspace
    */
  def close(workspaceId: WorkspaceId): Future[Boolean]

  /** Used to wait for the given files (relative filenames) to be uploaded into the workspace.
    *
    * The returned future completes with the directory path containing the files once they are available.
    *
    * @param workspaceId
    * @param fileDependencies
    * @return
    */
  def await(workspaceId: WorkspaceId, fileDependencies: Set[String]): Future[Path]

  /**
    * Saves a files to the given workspace
    * @param workspaceId
    * @param fileName
    * @param src
    * @return
    */
  def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]): Future[Boolean]

  /**
    * @return all known workspace ids
    */
  def list(): Future[List[WorkspaceId]]

  /**
    * convenience method for uploading to the workspace
    * @param workspaceId
    * @param file
    * @return
    */
  final def upload(workspaceId: WorkspaceId, file: Upload): Future[Boolean] = upload(workspaceId, file.name, file.source)
}

object WorkspaceClient {

  /**
    *
    * @param uploadDir
    * @param sys
    * @return an asynchronous, actor-based client
    */
  def apply(uploadDir: Path, sys: ActorRefFactory): WorkspaceClient = {
    val actor = sys.actorOf(Props(new WorkspaceEndpointActor(uploadDir)))
    import sys.dispatcher
    new ActorClient(actor)
  }

  class ActorClient(endpointActor: ActorRef)(implicit ec: ExecutionContext) extends WorkspaceClient {
    override def list = {
      val promise = Promise[List[String]]()
      endpointActor ! ListWorkspaces(promise)
      promise.future
    }
    override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]): Future[Boolean] = {
      val promise = Promise[Boolean]()
      endpointActor ! UploadFile(workspaceId, fileName, src, promise)
      promise.future
    }

    override def close(workspaceId: WorkspaceId) = {
      val promise = Promise[Boolean]()
      endpointActor ! Close(workspaceId, promise)
      promise.future
    }

    override def await(workspaceId: WorkspaceId, fileDependencies: Set[String]) = {
      val promise = Promise[Path]()
      endpointActor ! AwaitUploads(workspaceId, fileDependencies, promise)

      val fut = promise.future
      fut.onComplete {
        case res =>
          println(s"$workspaceId done for $fileDependencies : $res")
          println()
      }
      fut
    }
  }

}
