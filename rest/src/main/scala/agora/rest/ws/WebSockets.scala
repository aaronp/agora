package agora.rest.ws

import akka.NotUsed
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.ws.{TextMessage, UpgradeToWebSocket}
import akka.stream.scaladsl.Sink
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.reactivestreams.{Subscriber, Subscription}

object WebSockets {

  class Delegate(underying: Subscriber[WebSocketEnvelope[String]]) extends Subscriber[WebSocketEnvelope[String]] {
    private var subscription: Subscription = null
    def getSubscription                    = Option(subscription)
    override def onError(throwable: Throwable): Unit = {
      underying.onError(throwable)
    }

    override def onComplete(): Unit = {
      underying.onComplete()
    }

    override def onNext(t: WebSocketEnvelope[String]): Unit = {
      underying.onNext(t)
    }

    override def onSubscribe(s: Subscription): Unit = {
      subscription = s
      subscription.request(10)
      underying.onSubscribe(s)
    }
  }

  def unapply[T: Encoder: Decoder](req: HttpRequest, subscriber: Subscriber[WebSocketEnvelope[T]]): Option[UpgradeToWebSocket] = {
    req.header[UpgradeToWebSocket].map { upgrade =>
      //Source.fromPublisher()

      val sink: Sink[WebSocketEnvelope[T], NotUsed] = Sink.fromSubscriber(subscriber)

//      upgrade.handleMessagesWith(flow)
      upgrade
    }
  }

  def asMessage[T: Encoder](msg: T): TextMessage = {
    val json = {
      val envelope = WebSocketEnvelope(msg)
      envelope.asJson
    }
    TextMessage(json.noSpaces)
  }

}
