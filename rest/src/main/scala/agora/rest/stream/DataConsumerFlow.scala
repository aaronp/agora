package agora.rest.stream

import agora.api.streams._
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.circe.{Decoder, Encoder}
import org.reactivestreams.Publisher

/**
  * Used to consume data flowing through a publisher. This can make available a Flow[Message, Message] which will publish
  * the data from the 'republish' processor and subscribe to client TakeNext/Cancel message.
  *
  */
case class DataConsumerFlow[T: Encoder: Decoder](name : String, republish: BaseProcessor[T], initialClientMessageTakeNext: Int = 10)
    extends HasProcessor[T, T] { simpleSubscriber =>

  override protected def underlyingProcessor = republish

  /**
    * A subscription to websocket client messages to explicitly take next/cancel the feed.
    *
    * The FeedSubscriber subscription should then feed that 'takeNext' back to the input publisher
    */
  private val controlMessageSubscriber: BaseSubscriber[ClientSubscriptionMessage] =
    BaseSubscriber[ClientSubscriptionMessage](initialClientMessageTakeNext) {
      case (self, Cancel) =>
        republish.cancel()

        // request the next client message
        self.request(1)
      case (self, TakeNext(n)) =>
        takeNext(n)

        // request the next client message
        self.request(1)
    }

  /**
    * @param mat
    * @return a flow which will publish json and receive control messages from its subscription
    */
  def flow(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    MessageFlow(republish, controlMessageSubscriber)
  }

  def takeNext(n: Long) = republish.request(n)

  override protected def underlyingSubscriber = republish

}
