package agora.rest.stream

import agora.api.streams.{BaseProcessor, BaseSubscriber, HasSubscriber}
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import io.circe.{Decoder, Encoder}

/**
  * Used to consume data flowing through a publisher
  *
  */
class DataConsumerFlow[T: Encoder: Decoder](val name: String, maxCapacity: Int, initialTakeNext: Int) extends HasSubscriber[T] { simpleSubscriber =>

  override def toString = name

  /**
    * @param mat
    * @return a flow which will publish json and receive control messages from its subscription
    */
  def flow(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    MessageFlow(republish, controlMessageSubscriber)
  }

  private val republish = BaseProcessor.withMaxCapacity[T](maxCapacity)

  def takeNext(n: Long) = republish.request(n)

  def cancel() = republish.cancel()

  /**
    * From clients to explicitly take next/cancel the feed.
    *
    * The FeedSubscriber subscription should then feed that 'takeNext' back to the input publisher
    */
  val controlMessageSubscriber = BaseSubscriber[ClientSubscriptionMessage](s"$name control messages", initialTakeNext) {
    case (self, Cancel) =>
      cancel()
      self.request(1)
    case (self, TakeNext(n)) =>
      takeNext(n)
      self.request(1)
  }

  override protected def underlyingSubscriber = republish
}
