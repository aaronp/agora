package agora.exec.workspace

import java.nio.file.Path
import java.time.ZoneOffset

import akka.actor.{ActorRef, Props}
import agora.io.implicits._
import agora.io.BaseActor

import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

/**
  * The entry point to the dependency system -- creates and destroys [[WorkspaceActor]]s to handle requests
  *
  * @param uploadDir
  */
private[workspace] class WorkspaceEndpointActor(uploadDir: Path, bytesReadyPollFrequency: FiniteDuration) extends BaseActor {

  override def receive: Receive = handle(Map.empty)

  def handle(workspaceById: Map[WorkspaceId, ActorRef]): Receive = {
    logger.debug(s"Handling ${workspaceById.size} workspaces")

    def handlerForId(id: WorkspaceId): ActorRef = {
      workspaceById.get(id) match {
        case Some(actor) => actor
        case None =>
          val workspaceDir = uploadDir.resolve(id)
          logger.debug(s"Creating new workspace '$id' under '$workspaceDir'")
          val newHandler = context.actorOf(Props(new WorkspaceActor(id, workspaceDir, bytesReadyPollFrequency)))
          context.become(handle(workspaceById.updated(id, newHandler)))
          newHandler
      }
    }

    // handler
    {
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
          val epoch = threshold.toEpochSecond(ZoneOffset.UTC) * 1000
          all.filter(_.lastModifiedMillis > epoch)
        }
        val beforeFiles = createdBefore.fold(afterFiles) { threshold =>
          val epoch = threshold.toEpochSecond(ZoneOffset.UTC) * 1000
          afterFiles.filter(_.lastModifiedMillis < epoch)
        }

        promise.tryComplete(Try(beforeFiles.map(_.fileName).toList))
      case msg: WorkspaceMsg => handlerForId(msg.workspaceId) ! msg
    }
  }
}

object WorkspaceEndpointActor {
  private[workspace] case class RemoveWorkspace(id: WorkspaceId)
}
