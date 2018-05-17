package lupin.pub.flatmap

import agora.io.Lazy
import com.typesafe.scalalogging.StrictLogging
import lupin.pub.sequenced.SequencedProcessorPublisherVerification.{PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS, testEnv}
import lupin.pub.sequenced.{SequencedProcessor, SequencedProcessorInstance, SequencedProcessorPublisherVerification}
import lupin.{Publishers, newContextWithThreadPrefix}
import org.reactivestreams.Publisher
import org.reactivestreams.tck.PublisherVerification
import org.testng.annotations.AfterTest

class FlatMapPublisherBlackboxVerificationTest extends PublisherVerification[String](testEnv, PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS) with StrictLogging {
  private val lazyCtxt = Lazy(newContextWithThreadPrefix(getClass.getSimpleName))

  implicit def ctxt = lazyCtxt.value

  @AfterTest
  def afterAll(): Unit = {
    lazyCtxt.foreach(_.shutdown())
  }

  override def createPublisher(elements: Long): Publisher[String] = {
    import lupin.implicits._


    val numParts = if (elements < 2) {
      1
    } else if (elements < 3) {
      2
    } else if (elements < 4) {
      3
    } else {
      4
    }

    val partSize: Long = elements / numParts
    val remainder = elements % numParts

    val sv = new SequencedProcessorPublisherVerification

    val check = (1 to numParts).map(_ => partSize).sum + remainder
    logger.debug(s"Creating test publisher w/ ${elements} elements")
    if (check != elements) {
      logger.error("" + check)
    }


    if (numParts < 2) {
      Publishers.of("hi").flatMap { _ =>
        sv.createPublisher(elements)
      }
    } else {
      Publishers.forValues(1 to numParts).flatMap {
        case `numParts` =>
          logger.debug(s"Creating inner, final publisher w/ ${partSize + remainder} elements")
          val offset = (numParts - 1) * partSize
          sv.createPublisher(partSize + remainder).map(s => (s.toInt + offset) + " final value")
        case page =>
          logger.debug(s"Creating inner publisher w/ ${partSize} elements")
          val offset = (page - 1) * partSize
          sv.createPublisher(partSize).map(s => (s.toInt + offset) + " inner value")
      }
    }
  }

  override def createFailedPublisher(): Publisher[String] = {
    val dp: SequencedProcessorInstance[String] = SequencedProcessor[String]
    val pub = new FlatMapPublisher[String, String](dp.valuesPublisher(), _ => ???)
    dp.onError(new Exception("bang"))
    pub
  }
}