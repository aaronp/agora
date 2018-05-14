package lupin.pub.sequenced

import agora.io.Lazy
import lupin.newContextWithThreadPrefix
import org.reactivestreams.tck.SubscriberWhiteboxVerification
import org.reactivestreams.{Subscriber, Subscription}
import org.testng.annotations.AfterTest

class DurableProcessorSubscriberWhiteboxVerification extends SubscriberWhiteboxVerification[String](DurableProcessorPublisherVerification.testEnv) {

  private val lazyCtxt = Lazy(newContextWithThreadPrefix(getClass.getSimpleName))

  implicit def ctxt = lazyCtxt.value

  @AfterTest
  def afterAll(): Unit = {
    lazyCtxt.foreach(_.shutdown())
  }
  override def createSubscriber(probe: SubscriberWhiteboxVerification.WhiteboxSubscriberProbe[String]): Subscriber[String] = {
    val dp = DurableProcessor[String]()
    dp.valuesPublisher()
      .subscribe(new Subscriber[String] {
        override def onSubscribe(s: Subscription): Unit = {
          probe.registerOnSubscribe(new SubscriberWhiteboxVerification.SubscriberPuppet() {
            override def triggerRequest(elements: Long): Unit = {
              s.request(elements)
            }

            override def signalCancel() = {
              s.cancel()
            }
          })
        }

        override def onNext(t: String): Unit = {
          probe.registerOnNext(t)
        }

        override def onError(t: Throwable): Unit = {
          probe.registerOnError(t)
        }

        override def onComplete(): Unit = {
          probe.registerOnComplete()
        }
      })
    dp
  }

  override def createElement(element: Int): String = {
    val elm = s"element nr $element"
    elm
  }
}
