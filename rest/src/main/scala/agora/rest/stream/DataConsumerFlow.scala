package agora.rest.stream

import agora.flow._
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}

/**
  *
  * Used to consume data flowing through a publisher. This can make available a Flow[Message, Message] which will publish
  * the data from the 'republish' processor and subscribe to client TakeNext/Cancel message.
  *
  * @param name                     the name of the consumer as specified when creating the subscriber via the [[StreamRoutes]]
  * @param underlyingRepublisher    this will subscribe to the [[DataUploadFlow]] and be subscribed to from the websocket to send
  *                                 messages down to the underlying [[agora.rest.client.StreamSubscriberWebsocketClient]]
  * @param initialRepublishTakeNext the number of control messages to initially pull
  * @tparam T
  */
case class DataConsumerFlow[T: Encoder: Decoder](name: String,
                                                 underlyingRepublisher: BaseProcessor[T],
                                                 initialRepublishTakeNext: Int = 0,
                                                 controlMsgTakeNext: Int = 10)
    extends StrictLogging {
  consumerFlow =>

  private val throttledRepublish: ThrottledPublisher[T] = {
    val tp = new ThrottledPublisher(underlyingRepublisher)
    tp.allowRequested(initialRepublishTakeNext)
    tp
  }

  def snapshot() = {
    DataConsumerSnapshot(name, underlyingRepublisher.snapshot())
  }

  /**
    * A subscription to websocket client messages to explicitly take next/cancel the feed.
    *
    * The FeedSubscriber subscription should then feed that 'takeNext' back to the input publisher
    */
  private[stream] val controlMessageSubscriber: BaseSubscriber[ClientSubscriptionMessage] = {
    val sub = new BaseSubscriber[ClientSubscriptionMessage] {

      override def onNext(value: ClientSubscriptionMessage): Unit = {
        logger.info(s"\n\tDataConsumer control message $value\n")
        value match {
          case Cancel =>
            logger.info("\n\tDataConsumer control message cancelling\n")
            throttledRepublish.cancel()
            request(1)
          case TakeNext(n) =>
            takeNextFromRepublisher(n)
            request(1)
        }
      }
    }
    sub.request(controlMsgTakeNext)
    sub
  }

  /**
    * @param mat
    * @return a flow which will publish json and receive control messages from its subscription
    */
  def flow(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    MessageFlow(throttledRepublish, controlMessageSubscriber)
  }

  def takeNextFromRepublisher(n: Long) = {
    logger.info(s"\n\tDataConsumer control message requesting $n\n")
    throttledRepublish.allowRequested(n)
  }

}
