package agora.flow

import agora.flow.DurableProcessorPublisherVerification._
import org.reactivestreams.Subscriber
import org.reactivestreams.tck.SubscriberWhiteboxVerification

import scala.concurrent.ExecutionContext.Implicits.global

class DurableProcessorSubscriberWhiteboxVerification extends SubscriberWhiteboxVerification[Int](testEnv) {
  override def createSubscriber(probe: SubscriberWhiteboxVerification.WhiteboxSubscriberProbe[Int]): Subscriber[Int] = {
    DurableProcessor[Int]()
  }

  override def createElement(element: Int): Int = element
}
