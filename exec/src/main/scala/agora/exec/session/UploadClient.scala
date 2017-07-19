package agora.exec.session

import java.nio.file.Path

import agora.exec.model.Upload
import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future, Promise}

trait UploadClient {
  def close(sessionId: SessionId): Future[Boolean]

  def await(sessionId: SessionId, fileDependencies: Set[String]): Future[Path]

  def upload(sessionId: SessionId, fileName: String, src: Source[ByteString, Any]): Future[Boolean]

  def list(): Future[List[SessionId]]

  final def upload(sessionId: SessionId, file: Upload): Future[Boolean] = upload(sessionId, file.name, file.source)
}

object UploadClient {
  def apply(uploadDir: Path, sys: ActorRefFactory): UploadClient = {
    val actor = sys.actorOf(Props(new SessionEndpointActor(uploadDir)))
    import sys.dispatcher
    new ActorClient(actor)
  }

  class ActorClient(endpointActor: ActorRef)(implicit ec: ExecutionContext) extends UploadClient {
    override def list = {
      val promise = Promise[List[String]]()
      endpointActor ! ListSessions(promise)
      promise.future
    }
    override def upload(sessionId: SessionId, fileName: String, src: Source[ByteString, Any]): Future[Boolean] = {
      val promise = Promise[Boolean]()
      endpointActor ! UploadFile(sessionId, fileName, src, promise)
      promise.future
    }

    override def close(sessionId: SessionId) = {
      val promise = Promise[Boolean]()
      endpointActor ! Close(sessionId, promise)
      promise.future
    }

    override def await(sessionId: SessionId, fileDependencies: Set[String]) = {
      val promise = Promise[Path]()
      endpointActor ! AwaitUploads(sessionId, fileDependencies, promise)

      val fut = promise.future
      fut.onComplete {
        case res =>
          println(s"$sessionId done for $fileDependencies : $res")
          println()
      }
      fut
    }
  }

}
