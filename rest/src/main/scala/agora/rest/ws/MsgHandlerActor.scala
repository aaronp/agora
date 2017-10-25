package agora.rest.ws

import agora.io.{BaseActor, Sources}
import akka.actor.{PoisonPill, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.stream.Materializer
import io.circe.{Decoder, Encoder}

import scala.concurrent.Future
import scala.reflect.ClassTag

object MsgHandlerActor {
  def props[T: Encoder: Decoder: ClassTag](listener: MessageConsumer[T])(implicit mat: Materializer) = {
    Props(new MsgHandlerActor[T](listener))
  }
}

/**
  * Actor-based logic for MessageConsumers
  */
class MsgHandlerActor[T: Encoder: Decoder: ClassTag](listener: MessageConsumer[T])(implicit mat: Materializer) extends BaseActor {

  import context.dispatcher

  //  /**
  //    * Extractor from json
  //    */
  object FromJsonString {
    import io.circe.syntax._
    import io.circe.parser._
    def unapply[T: Encoder: Decoder](json: String): Option[WebSocketEnvelope[T]] = {
      //decode[WebSocketEnvelope[T]]().right.toOption
      decode[WebSocketEnvelope[T]](json).right.toOption
    }
  }

  override def receive: Receive = {
    case TextMessage.Strict(FromJsonString(env: WebSocketEnvelope[T])) =>
      listener.onMessage(env)
    case msg: TextMessage =>
      val jsonFuture: Future[String] = msg.textStream.runReduce(_ ++ _)
      onJson(jsonFuture)
    case env: WebSocketEnvelope[T] => listener.onMessage(env)
    case msg: BinaryMessage =>
      val jsonFuture: Future[String] = Sources.asText(msg.dataStream)
      onJson(jsonFuture)
  }

  def onJson(jsonFuture: Future[String]) = {

    jsonFuture.foreach {
      case FromJsonString(env: WebSocketEnvelope[T]) =>
        self ! env //WebSocketEnvelope(id, msg, remaining)
      case other =>
        listener.onError(new UnmarshalException[T](other))
        self ! PoisonPill
    }
  }

  override def postStop(): Unit = {
    listener.onClose()
  }

  override def unhandled(message: Any): Unit = {
    listener.onError(new IllegalStateException(s"${getClass.getSimpleName}(s${self.path}) couldn't handle $message"))
    super.unhandled(message)
  }

}
