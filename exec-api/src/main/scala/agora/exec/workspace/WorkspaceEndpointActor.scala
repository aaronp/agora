package agora.exec.workspace

import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.time.ZoneOffset

import agora.io.BaseActor
import agora.io.implicits._
import akka.actor.{ActorRef, Props}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

/**
  * The entry point to the dependency system -- creates and destroys [[WorkspaceActor]]s to handle requests
  *
  * @param uploadDir the containing directory under which all workspaces will be written
  * @param bytesReadyPollFrequency when file dependencies are ready, but the expected size does not match (e.g. the file has not been flushed)
  * @param workspaceAttributes the file permissions used to create new workspace directories
  *
  */
private[workspace] class WorkspaceEndpointActor(uploadDir: Path, bytesReadyPollFrequency: FiniteDuration, workspaceAttributes: Set[FileAttribute[_]])
    extends BaseActor {

  override def receive: Receive = handle(Map.empty)

  def handle(workspaceById: Map[WorkspaceId, ActorRef]): Receive = {
    logger.debug(s"Handling ${workspaceById.size} workspaces")

    def handlerForId(id: WorkspaceId): ActorRef = {
      workspaceById.get(id) match {
        case Some(actor) => actor
        case None =>
          val workspaceDir = uploadDir.resolve(id)
          logger.debug(s"Creating new workspace '$id' under '$workspaceDir'")
          val newHandler = context.actorOf(Props(new WorkspaceActor(id, workspaceDir, bytesReadyPollFrequency, workspaceAttributes)))
          context.become(handle(workspaceById.updated(id, newHandler)))
          newHandler
      }
    }

    // handler
    def onMessage(owt: Any): Unit = {
      owt match {
        case WorkspaceEndpointActor.RemoveWorkspace(id) =>
          context.become(handle(workspaceById - id))
        case msg @ Close(id, _, _, promise) =>
          workspaceById.get(id) match {
            case Some(handler) =>
              handler ! msg

            case None => promise.tryComplete(Success(false))
          }
        case ListWorkspaces(createdAfter, createdBefore, promise) =>
          val all = uploadDir.children

          val afterFiles = createdAfter.fold(all) { threshold =>
            val epoch = threshold.toEpochSecond * 1000
            all.filter(_.lastModifiedMillis > epoch)
          }
          val beforeFiles = createdBefore.fold(afterFiles) { threshold =>
            val epoch = threshold.toEpochSecond * 1000
            afterFiles.filter(_.lastModifiedMillis < epoch)
          }

          promise.tryComplete(Try(beforeFiles.map(_.fileName).toList))
        case msg: WorkspaceMsg => handlerForId(msg.workspaceId) ! msg
      }
    }

    {
      case msg =>
        logger.debug(s"onMessage: $msg\n")
        onMessage(msg)
    }
  }
}

object WorkspaceEndpointActor {

  private[workspace] case class RemoveWorkspace(id: WorkspaceId)

}
