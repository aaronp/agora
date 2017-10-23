package agora.rest.ws

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.HttpMessage.HttpMessageScalaDSLSugar
import akka.stream.Materializer
import io.circe.{Decoder, Encoder}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.reflect.ClassTag

trait MessageConsumer[T] {
  def onMessage(msg: WebSocketEnvelope[T]): Unit

  def onError(err: Throwable): Unit

  def onClose(): Unit
}

object MessageConsumer {

  def asSubscriber[T](c: MessageConsumer[T]): Subscriber[WebSocketEnvelope[T]] = {
    new Subscriber[WebSocketEnvelope[T]] {
      override def onError(throwable: Throwable): Unit = {
        c.onError(throwable)
      }

      override def onComplete(): Unit = {
        c.onClose()
      }

      override def onNext(msg: WebSocketEnvelope[T]): Unit = {
        c.onMessage(msg)
      }

      override def onSubscribe(subscription: Subscription): Unit = {}
    }
  }

}

trait MessageProducer[T] {
  def send(env: WebSocketEnvelope[T]): Unit

  def close(): Unit
}

object MessageProducer {

  def direct[T](consumer: MessageConsumer[T]): MessageProducer[T] = {
    new MessageProducer[T] {
      override def send(env: WebSocketEnvelope[T]): Unit = {
        consumer.onMessage(env)
      }

      override def close(): Unit = {
        consumer.onClose()
      }
    }
  }

  def apply[T: Encoder: Decoder: ClassTag](listener: MessageConsumer[T])(implicit system: ActorSystem,
                                                                         mat: Materializer): MessageProducer[T] = {
    apply(MsgHandlerActor.props(listener))
  }

  def apply[T](props: Props)(implicit system: ActorSystem): MessageProducer[T] = {
    forActor(system.actorOf(props))
  }

  private def forActor[T](msgHandlerActor: ActorRef): MessageProducer[T] = {
    ???
  }

}

trait WebsocketEndpoint[T] extends MessageProducer[T] with MessageConsumer[T]
