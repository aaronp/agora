package agora.flow

import agora.flow.DurableProcessorPublisherVerification._
import agora.flow.impl.DurableProcessorInstance
import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}

import scala.concurrent.ExecutionContext.Implicits.global

class DurableProcessorPublisherVerification extends PublisherVerification[String](testEnv, PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS) {
  override def createPublisher(elements: Long): Publisher[String] = {

    val dp: DurableProcessorInstance[String] = DurableProcessor[String]
    var i = 0L
    while (i < elements) {
      i = i + 1
      dp.onNext("" + i)
    }
    dp.onComplete()

    dp
  }

  override def createFailedPublisher(): Publisher[String] = {
    val dp: DurableProcessorInstance[String] = DurableProcessor[String]
    dp.onError(new Exception("bang"))
    dp
  }
}

object DurableProcessorPublisherVerification {

  val DEFAULT_TIMEOUT_MILLIS = 100L
  val DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS: Long = DEFAULT_TIMEOUT_MILLIS
  val PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 500L

  val testEnv = new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS)
}
