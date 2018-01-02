package agora.rest.stream

import agora.api.streams.{BasePublisher, BaseSubscriber, ConsumerQueue}
import agora.rest.exchange.ClientSubscriptionMessage
import agora.rest.exchange.ClientSubscriptionMessage._
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import org.reactivestreams.Subscriber

/**
  * Contains a publisher which will sent [[ClientSubscriptionMessage]] control flow messages and a subscriber to
  * [A] which will republish messages to a delegatingPublisher.
  *
  * @param name
  * @param maxCapacity
  * @param initialTakeNext
  */
class DataUploadFlow[A: Decoder: Encoder](val name: String, maxCapacity: Int, initialTakeNext: Int) extends StrictLogging { simplePublisher =>

  override def toString = name

  /**
    * The flow is based on json messages coming in, published to the DelegateSubscriber, and control flow messages
    * sent out.
    *
    * @param mat
    * @return a message flow for the entrypoint
    */
  def flow(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    MessageFlow(UpstreamMessagePublisher, DelegatingSubscriber)
  }

  // this sends (publishes) messages up-stream
  private object UpstreamMessagePublisher extends BasePublisher[ClientSubscriptionMessage] {
    override def toString = "upstreamMessagePublisher"

    override def newDefaultSubscriberQueue() = ConsumerQueue.withMaxCapacity(maxCapacity)

    override protected def newSubscription(s: Subscriber[_ >: ClientSubscriptionMessage]) = {
      val subscription = super.newSubscription(s)

      if (initialTakeNext > 0) {
        subscription.onElement(TakeNext(initialTakeNext))
      }
      subscription
    }
  }

  // this publisher re-publishes consumed messages
  object delegatingPublisher extends BasePublisher[A] {
    override def toString = "delegatingPublisher"

    override def onRequestNext(subscription: BasePublisher.BasePublisherSubscription[A], requested: Long) = {
      val nrToTake = super.onRequestNext(subscription, requested)
      if (nrToTake > 0) {
        takeNext(nrToTake)
      }
      nrToTake
    }

    override def newDefaultSubscriberQueue() = ConsumerQueue.withMaxCapacity[A](maxCapacity)
  }

  def takeNextJson(n: Long) = {
    import agora.rest.exchange.ClientSubscriptionMessage
    val tnMsg: ClientSubscriptionMessage = TakeNext(n)
    tnMsg.asJson
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

  // this will receive messages sent from the publishing web socket
  object DelegatingSubscriber extends BaseSubscriber[A] {
    override def toString = s"Delegate Subscriber for $simplePublisher"

    override def onNext(value: A): Unit = {
      logger.debug(s"$name received $value")
      delegatingPublisher.publish(value)
      request(1)
    }
  }

}
