package lupin.pub.sequenced

import agora.io.Lazy
import lupin.newContextWithThreadPrefix
import org.reactivestreams.tck.SubscriberBlackboxVerification
import org.testng.annotations.AfterTest

class SequencedProcessorSubscriberBlackboxVerification extends SubscriberBlackboxVerification[Int](SequencedProcessorPublisherVerification.testEnv) {

  private val lazyCtxt = Lazy(newContextWithThreadPrefix(getClass.getSimpleName))

  implicit def ctxt = lazyCtxt.value

  @AfterTest
  def afterAll(): Unit = {
    lazyCtxt.foreach(_.shutdown())
  }
  override def createSubscriber() = {
    val dp: SequencedProcessorInstance[Int] = SequencedProcessor[Int]()
    dp.requestIndex(10)
    dp
  }

  override def createElement(element: Int): Int = element
}
