package agora.exec.workspace

import java.nio.file.Path

import agora.io.BaseActor
import agora.io.implicits._
import akka.actor.PoisonPill
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.{Logger, StrictLogging}

import scala.concurrent.Future
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

  override def receive: Receive = handler(WorkspaceActorState(Set.empty, None))

  implicit def sys = context.system

  implicit lazy val materializer = ActorMaterializer()

  implicit def ctxt = materializer.executionContext

  def createdWorkspaceDir: Path = potentiallyNotExistentDir.mkDirs()

  def workspaceDirectory = WorkspaceDirectory(potentiallyNotExistentDir)

  private case class AwaitUploadsTimeout(msg: AwaitUploads)

  /**
    * A triggered check cancels the 'nextCheck'
    *
    */
  private def onTriggerUploadCheck(inputState: WorkspaceActorState) = {

    inputState.nextCheck.foreach(_.cancel())

    val updatedState = if (inputState.pendingRequests.nonEmpty) {
      logger.debug(s"Upload check triggered - trying to run ${inputState.pendingRequests.size} pending files for workspace $id")
      inputState.pendingRequests.foldLeft(inputState) {
        case (nextState, awaitMsg) =>
          val dependencyCheck: DependencyCheck = workspaceDirectory.dependencyStates(awaitMsg.dependencies.dependsOnFiles)

          if (dependencyCheck.canRun(awaitMsg.dependencies.awaitFlushedOutput)) {
            notifyWorkspaceReady(awaitMsg)
            nextState.remove(awaitMsg)
          } else {
            nextState
          }
      }
    } else {
      logger.debug(s"Upload check triggered, but no files to check for workspace $id")
      inputState
    }
    context.become(handler(updatedState.copy(nextCheck = None)))
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
    workspaceDirectory.onUpload(msg) { res =>
      logger.debug(s"Workspace $id upload $msg completed w/ $res, triggering check")

      val before = msg.result.toString
      val ok     = msg.result.tryComplete(res)

      logger.debug(s"Workspace Competing: result future w/ $ok, msg.result changed from $before to ${msg.result}")

      self ! TriggerUploadCheck(id)
    }

  }

  def handler(state: WorkspaceActorState): Receive = {
    logger.trace(s"Workspace $id in state ==> $state")

    val r: Receive = {
      case msg: WorkspaceMsg =>
        logger.trace(s"Workspace $id on $msg")
        onWorkspaceMsg(msg, state)
      case AwaitUploadsTimeout(msg @ AwaitUploads(dependencies, promise)) =>
        logger.warn(s"Workspace $id on Timeout: $msg")
        val status: DependencyCheck = workspaceDirectory.dependencyStates(dependencies.dependsOnFiles)
        if (status.canRun(dependencies.awaitFlushedOutput)) {
          logger.error(s"Workspace timed-out after ${dependencies.timeout} but can actually run w/ ${status}")
          notifyWorkspaceReady(msg)
        } else {
          logger.error(s"Workspace $id timed-out after ${dependencies.timeout} w/ ${status}")
          val err = WorkspaceDependencyTimeoutException(dependencies, status.dependencyStates)
          promise.tryComplete(Failure(err))
        }
        context.become(handler(state.remove(msg)))
      case other =>
        logger.error(s"Workspace $id DOESN'T KNOW HOW TO HANDLE $other")
        scala.sys.error(s"Workspace $id DOESN'T KNOW HOW TO HANDLE $other")
    }

    r
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
    * For this function to be called, we've already determined that 'canRun' is false for the given dependencies.
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

    logger.trace(s"Workspace '$id' on $msg\n\tw/ ${pendingRequests.size} pending requests")

    // handler
    msg match {
      case TriggerUploadCheck(_) => onTriggerUploadCheck(state)
      case MarkAsComplete(_, fileSizeByFileName) =>
        markAsComplete(fileSizeByFileName)

        if (fileSizeByFileName.nonEmpty) {
          onTriggerUploadCheck(state)
        }
      case msg @ AwaitUploads(UploadDependencies(_, dependsOnFiles, _, awaitFlushedOutput), promise) =>
        logger.debug(s"Workspace '$id' waiting on ${dependsOnFiles} w/ future: ${promise.future}")
        val dependencyCheck: DependencyCheck = workspaceDirectory.dependencyStates(dependsOnFiles)

        if (dependencyCheck.canRun(awaitFlushedOutput)) {
          notifyWorkspaceReady(msg)
          context.become(handler(state.remove(msg)))
        } else {
          logger.debug(dependencyCheck.dependencyStates.mkString(s"WORKSPACE $id Having to await uploads due to ", "\n", ""))

          onAwaitUploadsWhenFileIsNotAvailable(msg, dependencyCheck, state)
        }
      case msg @ UploadFile(_, _, _, _) => onUpload(msg)
      case closeMsg @ Close(_, ifNotModifiedSince, failPendingDependencies, promise) =>
        logger.info(s"Closing w/ $closeMsg")
        val ok = WorkspaceClient.closeWorkspace(potentiallyNotExistentDir, ifNotModifiedSince, failPendingDependencies, pendingRequests)
        promise.tryComplete(Success(ok))
        if (ok) {
          context.parent ! WorkspaceEndpointActor.RemoveWorkspace(id)
          self ! PoisonPill
        } else {
          logger.debug(s"Can't close workspace $id given $closeMsg w/ ${pendingRequests.size} pending requests: ")
        }
    }
  }

  def notifyWorkspaceReady(awaitUpload: AwaitUploads) = {
    val futId = System.identityHashCode(awaitUpload.workDirResult.future)
    logger.debug(
      s"Notifying that ${awaitUpload.dependencies.dependsOnFiles.mkString("[", ",", "]")} futId ($futId) are ready in workspace ${id} under $potentiallyNotExistentDir")

    val ok = awaitUpload.workDirResult.tryComplete(Try(createdWorkspaceDir))
    if (ok) {
      logger.info(s"Notified workspace '${id}' futId ($futId) files ${awaitUpload.dependencies.dependsOnFiles
        .mkString("[", ",", "]")} ready under $createdWorkspaceDir w/ ${createdWorkspaceDir.children.map(_.fileName).mkString(",")}")
    } else {
      logger.error(s"Could NOT Notify workspace '${id}' futId ($futId) files ${awaitUpload.dependencies.dependsOnFiles
        .mkString("[", ",", "]")} ready under $createdWorkspaceDir w/ ${createdWorkspaceDir.children.map(_.fileName).mkString(",")}")
    }
  }

}

object WorkspaceActor extends StrictLogging {

  def actorLogger: Logger = logger

}
