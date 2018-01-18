package agora.rest.stream

import agora.flow.BasePublisher.BasePublisherSubscription
import agora.flow._
import agora.rest.exchange.ClientSubscriptionMessage
import agora.rest.exchange.ClientSubscriptionMessage._
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}

/**
  * Contains a publisher which will sent [[ClientSubscriptionMessage]] control flow messages and a subscriber to
  * [A] which will republish messages to a delegatingPublisher.
  *
  * @param name
  */
class DataUploadFlow[NewQ[_]: AsConsumerQueue, A: Decoder: Encoder](val name: String,
                                                                    newQueueArgs: NewQ[A],
                                                                    clientSubscriptionMessagePublisher: HistoricProcessor[ClientSubscriptionMessage])
    extends StrictLogging {
  simplePublisher =>

  private val mkNewQueue: AsConsumerQueue[NewQ] = AsConsumerQueue[NewQ]()

  def snapshot(): DataUploadSnapshot = {

    // TODO - lift '.snapshot' into a trait so any instance can provide one
    val clientSnap = clientSubscriptionMessagePublisher match {
      case inst: HistoricProcessor.Instance[ClientSubscriptionMessage] => inst.snapshot()
      case _                                                           => PublisherSnapshot[Int](Map.empty)
    }
    DataUploadSnapshot(name, republishingSubscriber.snapshot(), clientSnap)
  }

  override def toString = name

  /**
    * The flow is based on json messages coming in, published to the DelegateSubscriber, and control flow messages
    * sent out.
    *
    * @return a message flow for the entrypoint
    */
  def flow(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    MessageFlow(clientSubscriptionMessagePublisher, RepublishingSubscriber)
  }

  // this sends (publishes) messages up-stream. It will be used by the 'flow' whose subscriber
  // will be determined by the akka http machinery to send 'ClientSubscriptionMessage' when we wanna
  // consume some more data from the client publisher
  //  private val clientSubscriptionMessagePublisher = HistoricProcessor(dao)

  def takeNext(n: Long): ClientSubscriptionMessage = {
    val json = TakeNext(n)
    logger.debug(s"$name takeNext($n) is sending : $json")
    clientSubscriptionMessagePublisher.onNext(json)
    json
  }

  def cancel(): ClientSubscriptionMessage = {
    logger.debug(s"$name Cancelling")
    val msg = Cancel
    clientSubscriptionMessagePublisher.onNext(msg)
    msg
  }

  def republishingSubscriber: BaseProcessor[A] = RepublishingSubscriber

  // this will receive messages sent from the publishing web socket
  private object RepublishingSubscriber extends BaseProcessor[A] {

    override def toString = s"RepublishingSubscriber for $simplePublisher"

    override protected def onRequestNext(subscription: BasePublisherSubscription[A], requested: Long): Long = {

      val nrToTake = super.onRequestNext(subscription, requested)
      logger.debug(s"RepublishingSubscriber.onRequestNext($requested) yields $nrToTake")
      if (nrToTake > 0) {
        takeNext(nrToTake)
      }
      nrToTake
    }

    override def newDefaultSubscriberQueue(): ConsumerQueue[A] = mkNewQueue.newQueue(newQueueArgs)
  }

}
