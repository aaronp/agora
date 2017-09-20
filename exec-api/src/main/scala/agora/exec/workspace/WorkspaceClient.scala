package agora.exec.workspace

import java.nio.file.Path

import agora.exec.model.Upload
import agora.exec.client.UploadClient
import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.duration.FiniteDuration
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
    *
    * @param workspaceId
    * @return a future which completes once the workspace has been cleaned up, with the boolean value signalling if it was a known/valid workspace
    */
  def close(workspaceId: WorkspaceId): Future[Boolean]

  /**
    * Saves a files to the given workspace
    *
    * @param workspaceId
    * @param fileName
    * @param src
    * @return
    */
  def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]): Future[Boolean]

  /**
    * triggers a check for uploads, should another process/event have updated the directory we're watching
    */
  def triggerUploadCheck(workspaceId: WorkspaceId): Unit

  /**
    * convenience method for uploading to the workspace
    *
    * @param workspaceId
    * @param file
    * @return
    */
  final def upload(workspaceId: WorkspaceId, file: Upload): Future[Boolean] = upload(workspaceId, file.name, file.source)

  /** Used to wait for the given files (relative filenames) to be uploaded into the workspace.
    *
    * The returned future completes with the directory path containing the files once they are available.
    *
    * @param dependencies the dependencies to await
    * @return a future of the local file path which contains the given workspace/files
    */
  def await(dependencies: UploadDependencies): Future[Path]

  final def await(workspace: WorkspaceId, dependsOnFiles: Set[String], timeoutInMillis: Long): Future[Path] = await(UploadDependencies(workspace, dependsOnFiles, timeoutInMillis))

  /**
    * @return all known workspace ids
    */
  def list(): Future[List[WorkspaceId]]
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

    override def triggerUploadCheck(workspaceId: WorkspaceId) = endpointActor ! TriggerUploadCheck(workspaceId)
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

    override def await(dependencies: UploadDependencies) = {
      val promise = Promise[Path]()
      endpointActor ! AwaitUploads(dependencies, promise)
      promise.future
    }
  }

}
