package agora.exec.workspace

import java.nio.file.Path

import agora.exec.dao.UploadDao
import agora.exec.model.Upload
import agora.io.implicits._
import akka.stream.ActorMaterializer

import concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Try}

/**
  * Handles messages sent from the [[WorkspaceEndpointActor]]
  *
  * @param id
  * @param initialDir the directory to use for this session, which initially may not exist
  */
private[workspace] class WorkspaceActor(val id: WorkspaceId, initialDir: Path) extends BaseActor {

  override def receive: Receive = handle(Nil)

  implicit def sys = context.system

  implicit lazy val materializer = ActorMaterializer()

  implicit def ctxt = context.dispatcher

  lazy val workspaceDir: Path = initialDir.mkDirs()

  private case class AwaitUploadsTimeout(msg: AwaitUploads)

  def onUpload(msg: UploadFile, pendingRequests: List[AwaitUploads]): Unit = {
    val UploadFile(_, file, src, promise) = msg
    val tri                               = Try(UploadDao(workspaceDir))
    val res = Future.fromTry(tri).flatMap { dao =>
      dao.writeDown(Upload(file, src) :: Nil)
    }
    res.onComplete {
      case uploadResult =>
        val kids = files
        val ok   = kids.contains(file)
        if (!ok) {
          logger.error(s"Upload to ${workspaceDir}/$file completed w/ ${uploadResult}, but ${kids.mkString(",")} doesn't contain $file!")
        } else {
          logger.debug(s"Upload to ${workspaceDir}/$file completed w/ ${uploadResult}, workspace now contains ${kids.mkString(",")}")
        }

        if (pendingRequests.nonEmpty) {
          val kids = files
          logger.debug(s"Trying to run ${pendingRequests.size} pending files after $file uploaded to workspace $id")
          pendingRequests.withFilter(canRun(_, kids)).foreach(self ! _)
        }
    }
    val uploadFuture = res.map(_ => true)
    promise.tryCompleteWith(uploadFuture)
  }

  def handle(pendingRequests: List[AwaitUploads]): Receive = {

    logger.debug(s"Handling workspace '$id' w/ ${pendingRequests.size} pending requests")

    // handler
    {
      case AwaitUploadsTimeout(AwaitUploads(dependencies, promise)) =>
        val errMsg = if (initialDir.exists) {
          val kids    = files
          val missing = dependencies.dependsOnFiles.filterNot(kids.contains)
          s"Still waiting for ${missing.size} files [${missing.mkString(",")}] in workspace '${dependencies.workspace}' after ${dependencies.timeout}"
        } else {
          s"No files have been uploaded to ${dependencies.workspace} after ${dependencies.timeout}"
        }
        promise.tryComplete(Failure(new Exception(errMsg)))
      case msg @ AwaitUploads(UploadDependencies(`id`, _, _), _) if canRun(msg, files) => notifyWorkspaceReady(msg)
      case msg @ AwaitUploads(UploadDependencies(`id`, _, timeout), _) =>
        logger.debug(s"waiting on $id")
        context.become(handle(msg :: pendingRequests))
        context.system.scheduler.scheduleOnce(timeout.millis, self, AwaitUploadsTimeout(msg))
      case msg @ UploadFile(`id`, _, _, _) => onUpload(msg, pendingRequests)
      case Close(`id`, promise) =>
        if (pendingRequests.nonEmpty) {
          logger.warn(s"Closing workspace $id with ${pendingRequests.size} pending files")
          val workspaceClosed = new IllegalStateException(s"Workspace '$id' has been closed")
          pendingRequests.foreach {
            case AwaitUploads(_, promise) => promise.failure(workspaceClosed)
          }
        }
        promise.tryComplete(Try(initialDir.exists).map(_ && Try(initialDir.delete()).isSuccess))
        context.stop(self)
    }
  }

  def files: Array[String] = {
    if (!initialDir.isDir) Array.empty else workspaceDir.children.map(_.fileName)
  }

  def notifyWorkspaceReady(schedule: AwaitUploads) = {
    logger.debug(s"Notifying that ${id} can run under $workspaceDir")
    schedule.workDirResult.tryComplete(Try(workspaceDir))
  }

  def canRun(schedule: AwaitUploads, uploads: => Array[String]) = {
    val dependencies = schedule.dependencies.dependsOnFiles
    if (dependencies.isEmpty) {
      true
    } else {
      lazy val all: Array[String] = files
      val missing: Set[String]    = dependencies.filterNot(all.contains)
      if (missing.isEmpty) {
        true
      } else {
        logger
          .debug(s"Can't run ${dependencies} under $initialDir as it's missing ${missing.size} dependencies : ${missing.mkString(",")}")
        false
      }
    }
  }

}
