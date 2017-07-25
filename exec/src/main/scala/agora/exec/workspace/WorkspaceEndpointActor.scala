package agora.exec.workspace

import java.nio.file.Path

import akka.actor.{ActorRef, Props}
import better.files._

import scala.util.{Success, Try}

/**
  * The entry point to the dependency system -- creates and destroys [[WorkspaceActor]]s to handle requests
  *
  * @param uploadDir
  */
private[workspace] class WorkspaceEndpointActor(uploadDir: Path) extends BaseActor {

  override def receive: Receive = handle(Map.empty)

  def handle(workspaceById: Map[WorkspaceId, ActorRef]): Receive = {
    logger.debug(s"Handling ${workspaceById.size} workspaces")

    def handlerForId(id: WorkspaceId): ActorRef = {
      workspaceById.get(id) match {
        case Some(actor) => actor
        case None =>
          import agora.io.implicits._
          val workspaceDir = uploadDir.resolve(id)
          logger.debug(s"Creating new workspace '$id' under '$workspaceDir'")
          val newHandler = context.actorOf(Props(new WorkspaceActor(id, workspaceDir)))
          context.become(handle(workspaceById.updated(id, newHandler)))
          newHandler
      }
    }

    // handler
    {
      case msg @ AwaitUploads(id, _, _)  => handlerForId(id) ! msg
      case msg @ UploadFile(id, _, _, _) => handlerForId(id) ! msg
      case ListWorkspaces(promise) =>
        promise.tryComplete(Try {
          val children = uploadDir.toFile.toScala.children.map(_.name)
          children.toList
        })
      case msg @ Close(id, promise) =>
        workspaceById.get(id) match {
          case Some(handler) =>
            handler ! msg
            context.become(handle(workspaceById - id))
          case None => promise.tryComplete(Success(false))
        }
    }
  }
}
