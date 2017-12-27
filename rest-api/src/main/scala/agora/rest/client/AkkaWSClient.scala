package agora.rest.client

import agora.api.streams.BasePublisher.BasePublisherSubscription
import agora.api.streams.{BaseProcessor, ConsumerQueue, HasConsumerQueue}
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import org.reactivestreams.Subscriber

import scala.concurrent.Future

/** contains the publishers/subscribers needed to setup a websocket message flow
  *
  */
class AkkaWSClient(subscriber: Subscriber[Json] with HasConsumerQueue[Json]) { wsClient =>

  // when we request/cancel our subscriptions, we end up sending a message upstream to take/cancel
  private val controlMessagePublisher: BaseProcessor[ClientSubscriptionMessage] = BaseProcessor(10)

  /** Convenience method to explicitly cancel outside of the subscription
    */
  def cancel() = {
    controlMessagePublisher.publish(ClientSubscriptionMessage.cancel)
  }

  /** Convenience method to explicitly request more work items outside of the subscription
    */
  def takeNext(n: Long) = {
    controlMessagePublisher.publish(ClientSubscriptionMessage.takeNext(1))
  }

  // this will be a subscriber of upstream messages and local (re-)publisher
  private val messageProcessor: BaseProcessor[Json] = new BaseProcessor[Json] {
    override def remove(id: Int): Unit = {
      wsClient.cancel()
      super.remove(id)
    }

    override protected def onRequestNext(subscription: BasePublisherSubscription[Json], requested: Long): Long = {
      val nrReq = super.onRequestNext(subscription, requested)
      wsClient.takeNext(requested)
      nrReq
    }

    override def newDefaultSubscriberQueue(): ConsumerQueue[Json] = ConsumerQueue.withMaxCapacity[Json](100)
  }

  val flow: Flow[Message, Message, NotUsed] = {
    val msgSrc: Source[Message, NotUsed] = Source.fromPublisher(controlMessagePublisher).map { msg =>
      TextMessage(msg.asJson.noSpaces)
    }
    val kitchen: Sink[Message, NotUsed] = Sink.fromSubscriber(messageProcessor).contramap { msg: Message =>
      msg match {
        case TextMessage.Strict(jsonText) =>
          parse(jsonText) match {
            case Left(err)   => sys.error(s"couldn't parse ${jsonText} : $err")
            case Right(json) => json
          }
        case other => sys.error(s"Expected a strict message but got " + other)
      }
    }

    Flow.fromSinkAndSource(kitchen, msgSrc)
  }

}

object AkkaWSClient {
  //"ws://echo.websocket.org"
  def apply[Mat](address: String, subscriber: Subscriber[Json] with HasConsumerQueue[Json])(implicit httpExp: HttpExt, mat: Materializer) = {
    val client          = new AkkaWSClient(subscriber)
    val (respFuture, _) = httpExp.singleWebSocketRequest(WebSocketRequest(address), client.flow)
    respFuture.map { upgradeResp =>
      upgradeResp.response -> client
    }
  }
}
