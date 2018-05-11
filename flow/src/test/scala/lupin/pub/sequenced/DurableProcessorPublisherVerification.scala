package lupin.pub.sequenced

import agora.io.Lazy
import lupin.newContextWithThreadPrefix
import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}
import org.testng.annotations.AfterTest
import DurableProcessorPublisherVerification._
import scala.util.{Failure, Success, Try}

class DurableProcessorPublisherVerification extends PublisherVerification[String](testEnv, PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS) {
  private val lazyCtxt = Lazy(newContextWithThreadPrefix(getClass.getSimpleName))

  implicit def ctxt = lazyCtxt.value

  @AfterTest
  def afterAll(): Unit = {
    lazyCtxt.foreach(_.shutdown())
  }

  class RangeDao(elements: Long) extends DurableProcessorDao[String] {
    override def markComplete(lastIndex: Long): Unit = {}

    override def lastIndex(): Option[Long] = Option(elements - 1)

    override def writeDown(index: Long, value: String): Boolean = {
      true
    }

    override def at(index: Long): Try[String] =
      if (index >= elements) {
        Failure(new IllegalArgumentException(s"Invalid index $index > $elements"))
      } else {
        Success(index + "")
      }

    override def minIndex(): Option[Long] = Option(0)
    override def maxIndex: Option[Long] = lastIndex()
  }

  override def createPublisher(elements: Long): Publisher[String] = {
    if (elements > 100) {
      val dao                                  = new RangeDao(elements)
      val dp: DurableProcessorInstance[String] = DurableProcessor[String](dao)
      dp.valuesPublisher()
    } else {
      val dp = DurableProcessor[String]()
      var i  = 0L
      while (i < elements) {
        i = i + 1
        dp.onNext("" + i)
      }
      dp.onComplete()
      dp.valuesPublisher()
    }
  }

  override def createFailedPublisher(): Publisher[String] = {
    val dp: DurableProcessorInstance[String] = DurableProcessor[String]
    dp.onError(new Exception("bang"))
    dp.valuesPublisher()
  }
}

object DurableProcessorPublisherVerification {

  val DEFAULT_TIMEOUT_MILLIS                     = 100L
  val DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS: Long    = DEFAULT_TIMEOUT_MILLIS
  val PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 500L

  val testEnv = new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS)
}
