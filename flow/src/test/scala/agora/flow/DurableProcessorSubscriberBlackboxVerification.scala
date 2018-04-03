package agora.flow

import agora.flow.DurableProcessorPublisherVerification._
import org.reactivestreams.tck.SubscriberBlackboxVerification

import scala.concurrent.ExecutionContext.Implicits.global

class DurableProcessorSubscriberBlackboxVerification extends SubscriberBlackboxVerification[Int](testEnv) {
  override def createSubscriber() = DurableProcessor[Int]()

  override def createElement(element: Int): Int = element
}


