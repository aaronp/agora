package agora.flow

import agora.flow.DurableProcessorPublisherVerification._
import agora.flow.impl.DurableProcessorInstance
import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class DurableProcessorPublisherVerification extends PublisherVerification[String](testEnv, PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS) {

  class RangeDao(elements: Long) extends DurableProcessorDao[String] {
    override def markComplete(lastIndex: Long): Unit = {

    }

    override def lastIndex(): Option[Long] = Option(elements - 1)

    override def writeDown(index: Long, value: String): Boolean = {
      true
    }

    override def at(index: Long): Try[String] = if (index >= elements) {
      Failure(new IllegalArgumentException(s"Invalid index $index > $elements"))
    } else {
      Success(index + "")
    }

    override def maxIndex: Option[Long] = lastIndex()
  }

  override def createPublisher(elements: Long): Publisher[String] = {
    if (elements > 100) {
      val dao = new RangeDao(elements)
      val dp: DurableProcessorInstance[String] = DurableProcessor[String](dao)
      dp
    } else {
      val dp = DurableProcessor[String]()
      var i = 0L
      while (i < elements) {
        i = i + 1
        dp.onNext("" + i)
      }
      dp.onComplete()
      dp
    }
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
