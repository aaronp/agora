package agora.rest.stream

import agora.api.streams.BasePublisher.BasePublisherSubscription
import agora.api.streams.KeyedPublisher.KeyedPublisherSubscription
import agora.api.streams._
import agora.rest.exchange.ClientSubscriptionMessage
import agora.rest.exchange.ClientSubscriptionMessage._
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json}

/**
  * Contains a publisher which will sent [[ClientSubscriptionMessage]] control flow messages and a subscriber to
  * [A] which will republish messages to a delegatingPublisher.
  *
  * @param name
  */
class DataUploadFlow[NewQ[_] : AsConsumerQueue, A: Decoder : Encoder](val name: String, newQueueArgs: NewQ[A]) extends StrictLogging {
  simplePublisher =>

  private val mkNewQueue: AsConsumerQueue[NewQ] = AsConsumerQueue[NewQ]()

  def snapshot() = {
    DataUploadSnapshot(name,
      delegatingPublisher.snapshot(),
      UpstreamMessagePublisher.snapshot())
  }

  override def toString = name

  /**
    * The flow is based on json messages coming in, published to the DelegateSubscriber, and control flow messages
    * sent out.
    *
    * @return a message flow for the entrypoint
    */
  def flow(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    MessageFlow(UpstreamMessagePublisher, DelegatingSubscriber)
  }


  // this sends (publishes) messages up-stream. It will be used by the 'flow' whose subscriber
  // will be determined by the akka http machinery to send 'ClientSubscriptionMessage' when we wanna
  // consume some more data from the client publisher
  private object UpstreamMessagePublisher extends BasePublisher[ClientSubscriptionMessage] {
    override def toString = "upstreamMessagePublisher"

    override def newDefaultSubscriberQueue() = ConsumerQueue(None)
  }

  def takeNext(n: Long): ClientSubscriptionMessage = {
    val json = TakeNext(n)
    logger.info(s"$name sending : $json")
    UpstreamMessagePublisher.publish(json)
    json
  }

  def cancel(): ClientSubscriptionMessage = {
    logger.info(s"$name Cancelling (replacing)")
    val msg = Cancel
    UpstreamMessagePublisher.publish(msg)
    msg
  }

  def delegatingPublisher = DelegatingSubscriber

  // this will receive messages sent from the publishing web socket
  private[stream] object DelegatingSubscriber extends BaseProcessor[A] {

    override def toString = s"Delegate Subscriber for $simplePublisher"

    override protected def onRequestNext(subscription: BasePublisherSubscription[A], requested: Long): Long = {
      val nrToTake = super.onRequestNext(subscription, requested)
      if (nrToTake > 0) {
        takeNext(nrToTake)
      }
      nrToTake
    }

    override def newDefaultSubscriberQueue(): ConsumerQueue[A] = mkNewQueue.newQueue(newQueueArgs)
  }

}