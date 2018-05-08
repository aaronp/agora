package lupin.pub.collate

import lupin.pub.impl.HasKey
import lupin.pub.sequenced.DurableProcessorDao
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext

/**
  * A publisher which can be subscribed to multiple publishers.
  *
  * Internally this would typically use a subscription-side queue (e.g. [[lupin.pub.passthrough.PassthroughPublisher]],
  * where each subscriber manages its own queue.
  *
  * @tparam K
  * @tparam T
  */
trait CollatingPublisher[K, T] extends Publisher[(K, T)] {

  /**
    * @return a new subscriber which can be used to subscribe to a Publisher of 'T' elements
    */
  def newSubscriber(key : K): Subscriber[T] with HasKey[K]

  def cancelSubscriber(key: K): Boolean

  def subscribers(): Set[K]

}

object CollatingPublisher {

  def apply[K, T](dao: DurableProcessorDao[(K, T)] = DurableProcessorDao[(K, T)](), propagateOnError: Boolean = true)(implicit execContext :ExecutionContext): CollatingPublisher[K, T] = {
    new CollatingPublisherInstance[K, T](dao, propagateOnError)
  }

}