package lupin.pub.collate

import lupin.data.HasKey
import lupin.pub.PublisherImplicits.RichPublisher
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
  def newSubscriber(key: K): Subscriber[T] with HasKey[K]

  /**
    * Cancel the subscription feed for the given key
    *
    * @param key the subscription to cancel
    * @return true if this call had an effect -- the subscription key was known and successfully cancelled
    */
  def cancelSubscriber(key: K): Boolean

  /** @return the IDs of all the current subscriptions
    */
  def subscribers(): Set[K]

  /**
    * Convenience function to expose a publisher for just the values 'T', without the keys
    *
    * @return a publisher of the values of type T
    */
  def valuesPublisher: Publisher[T] = {
    import lupin.implicits._
    val x: RichPublisher[(K, T), Publisher] = asRichPublisher(this)
    //val x : RichPublisher[(K, T), CollatingPublisher[K, T]] = asRichPublisher(this)
    x.map(_._2)
  }

}

object CollatingPublisher {

  def apply[K, T](dao: DurableProcessorDao[(K, T)] = DurableProcessorDao[(K, T)](), propagateOnError: Boolean = true, fair: Boolean)(
    implicit execContext: ExecutionContext) = {
    new CollatingPublisherInstance[K, T](dao, propagateOnError, fair)
  }

}
