package lupin.pub.sequenced

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.Subscriber

import scala.concurrent.ExecutionContext

/**
  * processor which lets you subscribe to values from a particular index.
  *
  * @tparam T
  */
trait SequencedProcessor[T] extends SequencedPublisher[T] with Subscriber[T]

object SequencedProcessor extends StrictLogging {

  def apply[T]()(implicit ec: ExecutionContext): SequencedProcessorInstance[T] = apply(DurableProcessorDao[T](), true)

  def apply[T](dao: DurableProcessorDao[T], propagateSubscriberRequestsToOurSubscription: Boolean = true)(implicit ec: ExecutionContext) = {
    new SequencedProcessorInstance[T](dao, propagateSubscriberRequestsToOurSubscription)
  }

  private[sequenced] def computeNumberToTake(lastReceivedIndex: Long, latest: Long, maxIndex: Long): Long = {
    val nrToTake = {
      val maxAvailable = maxIndex.min(latest)
      val nr           = (maxAvailable - lastReceivedIndex).max(0)
      logger.trace(s"""
           |Will try to pull $nr :
           |              last received index : $lastReceivedIndex
           |  max index of published elements : $latest
           |  currently requested up to index : $maxIndex
           |                 limit to pull to : $maxAvailable
             """.stripMargin)
      nr
    }
    nrToTake
  }

}
