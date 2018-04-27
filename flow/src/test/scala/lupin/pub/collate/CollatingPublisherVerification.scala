package lupin.pub.collate

import agora.io.Lazy
import lupin.newContextWithThreadPrefix
import lupin.pub.sequenced.DurableProcessorPublisherVerification.{PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS, testEnv}
import lupin.pub.sequenced.{DurableProcessor, DurableProcessorDao, DurableProcessorInstance}
import org.reactivestreams.Publisher
import org.reactivestreams.tck.PublisherVerification
import org.testng.annotations.AfterTest

import scala.util.{Failure, Success, Try}

class CollatingPublisherVerification extends PublisherVerification[String](testEnv, PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS) {
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

    override def maxIndex: Option[Long] = lastIndex()
  }

  override def createPublisher(elements: Long): Publisher[String] = {
    val upstream = if (elements > 100) {
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

    val cp = CollatingPublisher[String, String]()
    upstream.subscribe(cp.newSubscriber("test"))
    cp

  }

  override def createFailedPublisher(): Publisher[String] = {
    val upstream = DurableProcessor[String]()
    val cp = CollatingPublisher[String, String]()
    upstream.subscribe(cp.newSubscriber("test"))
    upstream.onError(new Exception("bang"))
    cp
  }
}