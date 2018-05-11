package lupin.pub.sequenced

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext

/**
  * processor which lets you subscribe to values from a particular index.
  *
  * @tparam T
  */
trait DurableProcessor[T] extends Publisher[(Long, T)] with Subscriber[T] {

  def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit

  /**
    * The default is to start subscribing from the first available index
    * @param subscriber
    */
  override def subscribe(subscriber: Subscriber[_ >: (Long, T)]) = subscribeFrom(firstIndex, subscriber)

  /** @return the first index available to read from, or -1 if none
    */
  def firstIndex: Long

  /**
    * @return the most-recently written index
    */
  def latestIndex: Option[Long]
}

object DurableProcessor extends StrictLogging {

  def apply[T]()(implicit ec: ExecutionContext): DurableProcessorInstance[T] = apply(DurableProcessorDao[T](), true)

  def apply[T](dao: DurableProcessorDao[T], propagateSubscriberRequestsToOurSubscription: Boolean = true)(implicit ec: ExecutionContext) = {
    new DurableProcessorInstance[T](dao, propagateSubscriberRequestsToOurSubscription)
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
