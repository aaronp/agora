package agora.exec.workspace

import java.nio.file.Path

import agora.api.time.Timestamp
import agora.exec.model.Upload
import agora.exec.workspace.WorkspaceActor.logger
import agora.io.dao.Timestamp
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Try}

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

object WorkspaceClient extends StrictLogging {

  val WorkspaceDispatcherName = "exec.dispatchers.io-dispatcher"

  def props(uploadDir: Path, sys: ActorRefFactory, bytesReadyPollFrequency: FiniteDuration) = {
    Props(new WorkspaceEndpointActor(uploadDir, bytesReadyPollFrequency)).withDispatcher(WorkspaceDispatcherName)
  }

  /**
    * @param uploadDir
    * @param sys
    * @param bytesReadyPollFrequency the time to wait before the expected file size matches the metadata file
    * @return an asynchronous, actor-based client
    */
  def apply(uploadDir: Path, sys: ActorSystem, bytesReadyPollFrequency: FiniteDuration): ActorClient = {
    val endpointActorProps = props(uploadDir, sys, bytesReadyPollFrequency)
    val actor = sys.actorOf(endpointActorProps)
    val execCtxt = sys.dispatchers.lookup(WorkspaceDispatcherName)
    new ActorClient(actor)(execCtxt)
  }

  class ActorClient(val endpointActor: ActorRef)(implicit ec: ExecutionContext) extends WorkspaceClient with StrictLogging {
    override def list(createdAfter: Option[Timestamp] = None, createdBefore: Option[Timestamp] = None) = {
      logger.debug(s"list(createdAfter=$createdAfter, createdBefore=$createdBefore)")
      val promise = Promise[List[String]]()
      endpointActor ! ListWorkspaces(createdAfter, createdBefore, promise)
      promise.future
    }

    override def triggerUploadCheck(workspaceId: WorkspaceId) = {
      logger.debug(s"triggerUploadCheck($workspaceId)")
      endpointActor ! TriggerUploadCheck(workspaceId)
    }

    override def markComplete(workspaceId: WorkspaceId, fileSizeByFileName: Map[String, Long]) = {
      logger.debug(s"markComplete($workspaceId, fileSizeByFileName=$fileSizeByFileName)")
      endpointActor ! MarkAsComplete(workspaceId, fileSizeByFileName)
    }

    override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]) = {
      logger.debug(s"upload($workspaceId, fileName=$fileName, src=$src)")
      val promise = Promise[(Long, Path)]()
      endpointActor ! UploadFile(workspaceId, fileName, src, promise)
      promise.future
    }

    override def close(workspaceId: WorkspaceId, ifNotModifiedSince: Option[Timestamp] = None, failPendingDependencies: Boolean = true) = {
      logger.debug(s"close($workspaceId, ifNotModifiedSince=$ifNotModifiedSince, failPendingDependencies=$failPendingDependencies)")
      val promise = Promise[Boolean]()
      endpointActor ! Close(workspaceId, ifNotModifiedSince, failPendingDependencies, promise)
      promise.future
    }

    override def awaitWorkspace(dependencies: UploadDependencies) = {
      logger.debug(s"awaitWorkspace($dependencies)")
      val promise = Promise[Path]()
      endpointActor ! AwaitUploads(dependencies, promise)
      promise.future
    }
  }

  /**
    * The logic for deleting a workspace
    *
    * @param potentiallyNotExistentDir the directory to potentially remove
    * @param ifNotModifiedSince        if specified, only remove if the most-recent file in the
    *                                  directory is older than this timestamp
    * @param failPendingDependencies   if true, any pending dependencies will be failed. If false, the
    *                                  workspace will NOT be closed (deleted) if there are any depdendencies
    * @param pendingRequests           the pendingRequests waiting for files in this potentiallyNotExistentDir
    * @return a flag indicating whether the delete was successful
    */
  def closeWorkspace(potentiallyNotExistentDir: Path,
                     ifNotModifiedSince: Option[Timestamp],
                     failPendingDependencies: Boolean,
                     pendingRequests: Set[AwaitUploads]): Boolean = {
    import agora.io.implicits._
    def workspaceId: WorkspaceId = potentiallyNotExistentDir.fileName

    val pendingOk = pendingRequests.isEmpty || failPendingDependencies

    val lastUsedOk = ifNotModifiedSince.fold(true) { timestamp =>
      allFilesAreOlderThanTime(potentiallyNotExistentDir, timestamp)
    }

    val canRemove = pendingOk && lastUsedOk

    /**
      * We're closing the workspace, potentially w/ jobs pending
      */
    if (pendingRequests.nonEmpty && failPendingDependencies) {
      logger.warn(s"Closing workspace $workspaceId with ${pendingRequests.size} pending files")
      val workspaceClosed = new IllegalStateException(s"Workspace '$workspaceId' has been closed")
      pendingRequests.foreach {
        case AwaitUploads(_, promise) => promise.failure(workspaceClosed)
      }
    }

    val ok: Try[Boolean] =
      Try(canRemove && potentiallyNotExistentDir.exists()).map(_ && Try(potentiallyNotExistentDir.delete()).isSuccess)
    ok == Success(true)
  }
}
