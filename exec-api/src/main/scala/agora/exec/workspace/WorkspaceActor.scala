package agora.exec.workspace

import java.nio.file.Path

import agora.api.time.Timestamp
import agora.io.BaseActor
import agora.io.implicits._
import akka.actor.{ActorRef, Cancellable, PoisonPill}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.{Logger, StrictLogging}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Handles messages sent from the [[WorkspaceEndpointActor]]
  *
  * @param id                        the workspace id
  * @param potentiallyNotExistentDir the directory to use for this session, which initially may not exist
  * @param bytesReadyPollFrequency   when file dependencies are ready, but the expected size does not match (e.g. the file has not been flushed)
  */
private[workspace] class WorkspaceActor(val id: WorkspaceId, potentiallyNotExistentDir: Path, bytesReadyPollFrequency: FiniteDuration) extends BaseActor {

  override protected val logger: Logger = {
    val lgr = WorkspaceActor.logger
    require(lgr != null)
    lgr
  }

  override def receive: Receive = handler(WorkspaceActorState(Set.empty, None))

  implicit def sys = context.system

  implicit lazy val materializer = ActorMaterializer()

  implicit def ctxt = context.dispatcher

  def createdWorkspaceDir: Path = potentiallyNotExistentDir.mkDirs()

  def workspaceDirectory = WorkspaceDirectory(potentiallyNotExistentDir)

  private case class AwaitUploadsTimeout(msg: AwaitUploads)

  /**
    * A triggered check cancels the 'nextCheck'
    *
    */
  private def triggerUploadCheck(state: WorkspaceActorState) = {

    state.nextCheck.foreach(_.cancel())

    if (state.pendingRequests.nonEmpty) {
      logger.debug(s"Upload check triggered - trying to run ${state.pendingRequests.size} pending files for workspace $id")
      state.pendingRequests.foreach(self ! _)
    } else {
      logger.debug(s"Upload check triggered, but no files to check for workspace $id")
    }
    context.become(handler(state.copy(nextCheck = None)))
  }

  def scheduleLater(delay: FiniteDuration, message: Any) = {
    context.system.scheduler.scheduleOnce(delay, self, message)
  }

  private def markAsComplete(fileSizeByFileName: Map[String, Long]) = {
    if (fileSizeByFileName.nonEmpty) {
      fileSizeByFileName.map {
        case (fileName, size) =>
          val file = potentiallyNotExistentDir.resolve(fileName)
          MetadataFile.createMetadataFileFor(file, Option(size))
      }
    }
  }

  /**
    * Handle an 'upload file' action. This will:
    *
    * 1) Upload the given [[UploadFile]] to the workspace and trigger a check for dependencies
    * 2) Save a <fileName>.metadata file to contain the file's metadata (e.g. size)s
    *
    * @param msg the file to upload
    */
  def onUpload(msg: UploadFile): Unit = {
    val savedFileFuture = workspaceDirectory.onUpload(msg)

    savedFileFuture.onComplete {
      case _ =>
        self ! TriggerUploadCheck(id)
    }

    msg.result.tryCompleteWith(savedFileFuture)
  }

  def handler(state: WorkspaceActorState): Receive = {
    case msg: WorkspaceMsg => onWorkspaceMsg(msg, state)
    case AwaitUploadsTimeout(msg @ AwaitUploads(dependencies, promise)) =>
      val status: DependencyCheck = workspaceDirectory.dependencyStates(dependencies.dependsOnFiles)
      if (status.canRun(dependencies.awaitFlushedOutput)) {
        logger.error(s"Workspace timed-out after ${dependencies.timeout} but can actually run w/ ${status}")
        notifyWorkspaceReady(msg)
      } else {
        logger.error(s"Workspace timed-out after ${dependencies.timeout} w/ ${status}")
        val err = WorkspaceDependencyTimeoutException(dependencies, status.dependencyStates)
        promise.tryComplete(Failure(err))
      }
      context.become(handler(state.remove(msg)))
  }

  /**
    * We're asked to wait for some files to become available.
    *
    * We check the '.<fileName>.metadata' file to exist for a given <fileName> in the workspace, and for its
    * contents to be the same as the file size of <fileName>.
    *
    * The workspace client is event-driven, triggered by files being uploaded or jobs completing. That said, jobs
    * are run out-of-process, and so the extra file-size check is a belt-and-braces check that all output is flushed.
    *
    * For this function to be called, we've alread determined that 'canRun' is false for the given dependencies.
    *
    * @param msg
    * @param state
    * @return
    */
  def onAwaitUploadsWhenFileIsNotAvailable(msg: AwaitUploads, dependencyCheck: DependencyCheck, state: WorkspaceActorState) = {

    def newCheck = state.nextCheck match {
      case None if msg.dependencies.awaitFlushedOutput && dependencyCheck.containsPollableResult =>
        logger.debug(s"Scheduling a re-check of results after $bytesReadyPollFrequency")
        val cancellable = scheduleLater(bytesReadyPollFrequency, TriggerUploadCheck(id))
        Option(cancellable)
      case existing =>
        logger.debug(s"NOT Scheduling a re-check given the existing check $existing and workspace state:\n$dependencyCheck\n\nfor\n$msg\n\n")
        existing
    }

    val newRequests = state.pendingRequests + msg
    require(newRequests.contains(msg))

    if (state.pendingRequests.contains(msg)) {
      logger.debug(s"We're already waiting on $msg")
    } else {
      logger.debug(s"Adding new pending request:S $msg")
    }

    scheduleLater(msg.dependencies.timeout, AwaitUploadsTimeout(msg))
    val newState = WorkspaceActorState(
      newRequests,
      newCheck
    )
    context.become(handler(newState))
  }

  def onWorkspaceMsg(msg: WorkspaceMsg, state: WorkspaceActorState) = {
    import state._

    logger.trace(s"Handling workspace '$id' w/ ${pendingRequests.size} pending requests")

    // handler
    msg match {
      case TriggerUploadCheck(_) =>
        triggerUploadCheck(state)
      case MarkAsComplete(_, fileSizeByFileName) =>
        markAsComplete(fileSizeByFileName)

        if (fileSizeByFileName.nonEmpty) {
          triggerUploadCheck(state)
        }
      case msg @ AwaitUploads(UploadDependencies(_, dependsOnFiles, _, awaitFlushedOutput), _) =>
        val dependencyCheck: DependencyCheck = workspaceDirectory.dependencyStates(dependsOnFiles)

        if (dependencyCheck.canRun(awaitFlushedOutput)) {
          notifyWorkspaceReady(msg)
          context.become(handler(state.remove(msg)))
        } else {
          logger.debug(dependencyCheck.dependencyStates.mkString("Having to await uploads due to ", "\n", ""))

          onAwaitUploadsWhenFileIsNotAvailable(msg, dependencyCheck, state)
        }
      case msg @ UploadFile(_, _, _, _) => onUpload(msg)
      case closeMsg @ Close(_, ifNotModifiedSince, failPendingDependencies, promise) =>
        logger.info(s"Closing w/ $closeMsg")
        val ok = WorkspaceActor.closeWorkspace(potentiallyNotExistentDir, ifNotModifiedSince, failPendingDependencies, pendingRequests)
        promise.tryComplete(Success(ok))
        if (ok) {
          context.parent ! WorkspaceEndpointActor.RemoveWorkspace(id)
          self ! PoisonPill
        } else {
          logger.debug(s"Can't close workspace $id given $closeMsg w/ ${pendingRequests.size} pending requests: ")
        }
    }
  }

  def notifyWorkspaceReady(schedule: AwaitUploads) = {
    logger.debug(
      s"Notifying that ${schedule.dependencies.dependsOnFiles.mkString("[", ",", "]")} are ready in workspace ${id} under $potentiallyNotExistentDir")
    schedule.workDirResult.tryComplete(Try(createdWorkspaceDir))
  }

}

object WorkspaceActor extends StrictLogging {

  def actorLogger: Logger = logger

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
  def closeWorkspace[AwaitUploads](potentiallyNotExistentDir: Path,
                                   ifNotModifiedSince: Option[Timestamp],
                                   failPendingDependencies: Boolean,
                                   pendingRequests: Set[AwaitUploads]): Boolean = {
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
