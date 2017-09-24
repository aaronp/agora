package agora.exec.workspace

import java.nio.file.Path

import agora.api.time.Timestamp
import agora.exec.model.Upload
import agora.io.BaseActor
import agora.io.implicits._
import akka.actor.PoisonPill
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Handles messages sent from the [[WorkspaceEndpointActor]]
  *
  * @param id                        the workspace id
  * @param potentiallyNotExistentDir the directory to use for this session, which initially may not exist
  */
private[workspace] class WorkspaceActor(val id: WorkspaceId, potentiallyNotExistentDir: Path) extends BaseActor {

  override def receive: Receive = handler(Nil)

  implicit def sys = context.system

  implicit lazy val materializer = ActorMaterializer()

  implicit def ctxt = context.dispatcher

  lazy val workspaceDir: Path = potentiallyNotExistentDir.mkDirs()

  private case class AwaitUploadsTimeout(msg: AwaitUploads)

  private def triggerUploadCheck(pendingRequests: List[AwaitUploads]) = {
    if (pendingRequests.nonEmpty) {
      lazy val kids = files
      logger.debug(s"Trying to run ${pendingRequests.size} pending files in $id")
      pendingRequests.withFilter(canRun(_, kids)).foreach(self ! _)
    }
  }

  def onUpload(msg: UploadFile, pendingRequests: List[AwaitUploads]): Unit = {
    val UploadFile(_, file, src, promise) = msg

    import akka.http.scaladsl.util.FastFuture._
    logger.info(s"Uploading ${file} to $id")
    val tri: Try[UploadDao.FileUploadDao] = Try(UploadDao(workspaceDir))
    val savedFileFuture = Future.fromTry(tri).fast.flatMap { dao =>
      val files = dao.writeDown(Upload(file, src) :: Nil)
      files.fast.map(_.ensuring(_.size == 1).head) // we only are writing down one
    }
    savedFileFuture.onComplete {
      case uploadResult =>
        val kids = files
        val ok   = kids.contains(file)
        if (!ok) {
          logger.error(s"Upload to ${workspaceDir}/$file completed w/ ${uploadResult}, but ${kids.mkString(",")} doesn't contain $file!")
        } else {
          logger.debug(s"Upload to ${workspaceDir}/$file completed w/ ${uploadResult}, workspace now contains ${kids.mkString(",")}")
        }

        self ! TriggerUploadCheck(id)
    }

    promise.tryCompleteWith(savedFileFuture)
  }

  def handler(pendingRequests: List[AwaitUploads]): Receive = {
    case msg: WorkspaceMsg => onWorkspaceMsg(msg, pendingRequests)
    case AwaitUploadsTimeout(AwaitUploads(dependencies, promise)) =>
      val errMsg = if (potentiallyNotExistentDir.exists) {
        val kids    = files
        val missing = dependencies.dependsOnFiles.filterNot(kids.contains)
        s"Still waiting for ${missing.size} files [${missing.mkString(",")}] in workspace '${dependencies.workspace}' after ${dependencies.timeout}"
      } else {
        s"No files have been uploaded to ${dependencies.workspace} after ${dependencies.timeout}"
      }
      promise.tryComplete(Failure(new Exception(errMsg)))
  }

  def onWorkspaceMsg(msg: WorkspaceMsg, pendingRequests: List[AwaitUploads]) = {

    logger.debug(s"Handling workspace '$id' w/ ${pendingRequests.size} pending requests")

    // handler
    msg match {
      case TriggerUploadCheck(_) => triggerUploadCheck(pendingRequests)
      case msg @ AwaitUploads(UploadDependencies(`id`, _, _), _) if canRun(msg, files) =>
        notifyWorkspaceReady(msg)
        val remaining = pendingRequests.filterNot(_ == msg)
        context.become(handler(remaining))
      case msg @ AwaitUploads(UploadDependencies(`id`, dependencyFiles, timeout), _) =>
        logger.info(s"waiting on $dependencyFiles in workspace '$id' for ${timeout}ms")
        context.become(handler(msg :: pendingRequests))
        context.system.scheduler.scheduleOnce(timeout.millis, self, AwaitUploadsTimeout(msg))
      case msg @ UploadFile(`id`, _, _, _) => onUpload(msg, pendingRequests)
      case closeMsg @ Close(`id`, ifNotModifiedSince, failPendingDependencies, promise) =>
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

  def files: Array[String] = potentiallyNotExistentDir.children.map(_.fileName)

  def notifyWorkspaceReady(schedule: AwaitUploads) = {
    logger.debug(s"Notifying that ${id} can run under $workspaceDir")
    schedule.workDirResult.tryComplete(Try(workspaceDir))
  }

  def canRun(schedule: AwaitUploads, uploads: => Array[String]) = {
    val dependencies = schedule.dependencies.dependsOnFiles
    if (dependencies.isEmpty) {
      true
    } else {
      lazy val all: Array[String] = uploads
      val missing: Set[String]    = dependencies.filterNot(all.contains)
      if (missing.isEmpty) {
        true
      } else {
        logger
          .debug(s"Can't run ${dependencies} under $potentiallyNotExistentDir as it's missing ${missing.size} dependencies : ${missing.mkString(",")}")
        false
      }
    }
  }
}

object WorkspaceActor extends StrictLogging {

  /**
    * The logic for deleting a workspace
    *
    * @param potentiallyNotExistentDir the directory to potentially remove
    * @param ifNotModifiedSince if specified, only remove if the most-recent file in the
    *                           directory is older than this timestamp
    * @param failPendingDependencies if true, any pending dependencies will be failed. If false, the
    *                                workspace will NOT be closed (deleted) if there are any depdendencies
    * @param pendingRequests the pendingRequests waiting for files in this potentiallyNotExistentDir
    * @return a flag indicating whether the delete was successful
    */
  def closeWorkspace[AwaitUploads](potentiallyNotExistentDir: Path,
                                   ifNotModifiedSince: Option[Timestamp],
                                   failPendingDependencies: Boolean,
                                   pendingRequests: List[AwaitUploads]): Boolean = {
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

    val ok: Try[Boolean] = Try(canRemove && potentiallyNotExistentDir.exists).map(_ && Try(potentiallyNotExistentDir.delete()).isSuccess)
    ok == Success(true)
  }
}
