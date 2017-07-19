package agora.exec.session

import java.nio.file.Path

import akka.actor.{ActorRef, Props}
import better.files._

import scala.util.Try

/**
  * The entry point to the dependency system -- creates and destroys [[SessionActor]]s to handle requests
  *
  * @param uploadDir
  */
private[session] class SessionEndpointActor(uploadDir: Path) extends BaseActor {

  override def receive: Receive = handle(Map.empty)

  def handle(sessionById: Map[SessionId, ActorRef]): Receive = {
    logger.debug(s"Handling ${sessionById.size} sessions")

    def handlerForId(id: SessionId): ActorRef = {
      sessionById.get(id) match {
        case Some(actor) => actor
        case None =>
          import agora.io.implicits._
          val sessionDir = uploadDir.resolve(id).mkDirs()
          logger.debug(s"Creating new session '$id' under '$sessionDir'")
          val newHandler = context.actorOf(Props(new SessionActor(id, sessionDir)), s"${id.filter(_.isLetterOrDigit)}")
          context.become(handle(sessionById.updated(id, newHandler)))
          newHandler
      }
    }

    // handler
    {
      case msg @ AwaitUploads(id, _, _)  => handlerForId(id) ! msg
      case msg @ UploadFile(id, _, _, _) => handlerForId(id) ! msg
      case ListSessions(promise) =>
        promise.tryComplete(Try {
          val children = uploadDir.toFile.toScala.children.map(_.name)
          children.toList
        })
      case msg @ Close(id, _) =>
        sessionById.get(id).foreach { handler =>
          handler ! msg
          context.become(handle(sessionById - id))
        }
    }
  }
}
