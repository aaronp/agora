package agora.rest.client

import agora.api.streams.BasePublisher.BasePublisherSubscription
import agora.api.streams.{BaseProcessor, ConsumerQueue, HasConsumerQueue}
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import org.reactivestreams.Subscriber

/**
  * contains the publishers/subscribers needed to setup a websocket message flow
  *
  * @param subscriber the subscriber to connect to the data coming from the websocket
  * @tparam S
  */
class StreamSubscriberWebsocketClient[S <: Subscriber[Json] with HasConsumerQueue[Json]](val subscriber: S) extends StrictLogging {
  wsClient =>

  // when we request/cancel our subscriptions, we end up sending a message upstream to take/cancel
  private val controlMessagePublisher: BaseProcessor[ClientSubscriptionMessage] = BaseProcessor(None)

  /** Convenience method to explicitly cancel outside of the subscription
    */
  def cancel() = {
    controlMessagePublisher.publish(ClientSubscriptionMessage.cancel)
  }

  /** Convenience method to explicitly request more work items outside of the subscription
    */
  def takeNext(n: Long = 1) = {
    controlMessagePublisher.publish(ClientSubscriptionMessage.takeNext(n))
  }

  // this will be a subscriber of upstream messages which republishes the messages to the subscriber
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

    override def newDefaultSubscriberQueue(): ConsumerQueue[Json] = sys.error("The subscriber's queue should always be used")
  }
  messageProcessor.subscribe(subscriber)

  val flow: Flow[Message, Message, NotUsed] = {
    val msgSrc: Source[Message, NotUsed] = Source.fromPublisher(controlMessagePublisher).map { msg =>
      val json = msg.asJson.noSpaces
      logger.debug(s"sending control message: $json")
      TextMessage(json)
    }
    val kitchen: Sink[Message, NotUsed] = Sink.fromSubscriber(messageProcessor).contramap { msg: Message =>
      msg match {
        case TextMessage.Strict(jsonText) =>
          logger.debug(s"received : $jsonText")
          parse(jsonText) match {
            case Left(err) => sys.error(s"couldn't parse ${jsonText} : $err")
            case Right(json) => json
          }
        case other => sys.error(s"Expected a strict message but got " + other)
      }
    }

    Flow.fromSinkAndSource(kitchen, msgSrc)
  }

}

object StreamSubscriberWebsocketClient extends StrictLogging {
  //"ws://echo.websocket.org"
  def openConnection(address: String, subscriber: Subscriber[Json] with HasConsumerQueue[Json])(implicit httpExp: HttpExt, mat: Materializer) = {
    import mat.executionContext

    val client = new StreamSubscriberWebsocketClient(subscriber)
    val (respFuture, _) = httpExp.singleWebSocketRequest(WebSocketRequest(address), client.flow)
    respFuture.map { upgradeResp =>
      val status = upgradeResp.response.status
      logger.debug(s"Upgraded subscriber websocket w/ status $status for $address: ${upgradeResp.response}")
      client
    }
  }
}
