package agora.exec.workspace

import java.nio.file.Path

import agora.exec.model.Upload
import agora.io.dao.Timestamp
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
  * They can also 'awaitWorkspace' files to become available in that workspace.
  */
trait WorkspaceClient {

  /**
    * closing the workspace releases the resources used -- e.g. deletes the relevant directory
    *
    * @param workspaceId             the workspace id to delete
    * @param ifNotModifiedSince      if specified, then the workspace will only be removed if its contents haven't
    *                                been modified since the given timestamp
    * @param failPendingDependencies if set, any pending dependency futures will fail and the workspace will be closed
    *                                (removed). If false, then the workspace will only be removed if there are no
    *                                pending depdendencies
    * @return a future which completes once the workspace has been cleaned up, with the boolean value signalling if it was a known/valid workspace
    */
  def close(workspaceId: WorkspaceId, ifNotModifiedSince: Option[Timestamp] = None, failPendingDependencies: Boolean = true): Future[Boolean]

  /**
    * Saves a files to the given workspace
    *
    * @param workspaceId
    * @param fileName
    * @param src
    * @return
    */
  def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]): Future[(Long, Path)]

  /**
    * triggers a check for uploads, should another process/event have updated the directory we're watching
    */
  def triggerUploadCheck(workspaceId: WorkspaceId): Unit

  /**
    * Marks the given files as complete under the workspace -- any job(s) which were writing to the files
    * should now be finished.
    *
    * For implementations, invoking this function should also trigger a call to 'triggerUploadCheck'
    *
    * @param workspaceId the workspace under which the files should exist
    * @param fileSizeByFileWritten       the files to mark as complete
    */
  def markComplete(workspaceId: WorkspaceId, fileSizeByFileWritten: Map[String, Long]): Unit

  /**
    * convenience method for uploading to the workspace
    *
    * @param workspaceId
    * @param file
    * @return
    */
  final def upload(workspaceId: WorkspaceId, file: Upload): Future[(Long, Path)] = upload(workspaceId, file.name, file.source)

  /** Used to wait for the given files (relative filenames) to be uploaded into the workspace.
    *
    * The returned future completes with the directory path containing the files once they are available.
    *
    * @param dependencies the dependencies to awaitWorkspace
    * @return a future of the local file path which contains the given workspace/files
    */
  def awaitWorkspace(dependencies: UploadDependencies): Future[Path]

  /**
    * @see [[UploadDependencies]] for a description of the arguments
    * @param awaitFlushedOutput [[UploadDependencies]] describes this in detail
    */
  final def awaitWorkspace(workspace: WorkspaceId, dependsOnFiles: Set[String], timeoutInMillis: Long, awaitFlushedOutput: Boolean = true): Future[Path] =
    awaitWorkspace(UploadDependencies(workspace, dependsOnFiles, timeoutInMillis, awaitFlushedOutput))

  /**
    * @return all known workspace ids
    */
  def list(createdAfter: Option[Timestamp] = None, createdBefore: Option[Timestamp] = None): Future[List[WorkspaceId]]
}

object WorkspaceClient {

  /**
    *
    * @param uploadDir
    * @param sys
    * @param bytesReadyPollFrequency the time to wait before the expected file size matches the metadata file
    * @return an asynchronous, actor-based client
    */
  def apply(uploadDir: Path, sys: ActorRefFactory, bytesReadyPollFrequency: FiniteDuration) = {
    val actor = sys.actorOf(Props(new WorkspaceEndpointActor(uploadDir, bytesReadyPollFrequency)))
    import sys.dispatcher
    new ActorClient(actor)
  }

  class ActorClient(val endpointActor: ActorRef)(implicit ec: ExecutionContext) extends WorkspaceClient {
    override def list(createdAfter: Option[Timestamp] = None, createdBefore: Option[Timestamp] = None) = {
      val promise = Promise[List[String]]()
      endpointActor ! ListWorkspaces(createdAfter, createdBefore, promise)
      promise.future
    }

    override def triggerUploadCheck(workspaceId: WorkspaceId) = endpointActor ! TriggerUploadCheck(workspaceId)

    override def markComplete(workspaceId: WorkspaceId, fileSizeByFileName: Map[String, Long]) =
      endpointActor ! MarkAsComplete(workspaceId, fileSizeByFileName)

    override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]) = {
      val promise = Promise[(Long, Path)]()
      endpointActor ! UploadFile(workspaceId, fileName, src, promise)
      promise.future
    }

    override def close(workspaceId: WorkspaceId, ifNotModifiedSince: Option[Timestamp] = None, failPendingDependencies: Boolean = true) = {
      val promise = Promise[Boolean]()
      endpointActor ! Close(workspaceId, ifNotModifiedSince, failPendingDependencies, promise)
      promise.future
    }

    override def awaitWorkspace(dependencies: UploadDependencies) = {
      val promise = Promise[Path]()
      endpointActor ! AwaitUploads(dependencies, promise)
      promise.future
    }
  }

}
