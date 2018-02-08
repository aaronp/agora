package agora.flow

import cats.Semigroup

/**
  * A publishing skeleton which exposes a 'publish' to push elements to subscribers.
  *
  * Elements will be enqueued w/ each subscriber, and subscribers will start receiving elements
  * from the point at which they subscribe
  *
  * As each subscriber's subscription will be 'taking next' independently,
  * we'll also want to potentially propagate 'takeNext' calls which can be used
  * for subscriptions which are feeding us.
  *
  * @tparam T
  */
trait BasePublisher[T] extends IntKeyedPublisher[T]

object BasePublisher {
  type BasePublisherSubscription[T] = KeyedPublisher.KeyedPublisherSubscription[Int, T]

  def apply[F[_], T](newQueueArgs: F[T])(implicit asQueue: AsConsumerQueue[F]): BasePublisher[T] = {
    new BasePublisher[T] {
      override def newDefaultSubscriberQueue() = asQueue.newQueue(newQueueArgs)
    }
  }

  def apply[T](maxCapacity: Int): BasePublisher[T] = {
    new BasePublisher[T] {
      override def newDefaultSubscriberQueue() = ConsumerQueue.withMaxCapacity(maxCapacity)
    }
  }

  /**
    * Note: This KeyedPublisher will conflate messages ONLY AFTER A SUBSCRIBER SUBSCRIBES.
    *
    * Any message arriving BEFORE a subscription is made will NOT be sent
    *
    * @param initialValue
    * @tparam T
    * @return
    */
  def apply[T: Semigroup](initialValue: Option[T] = None): BasePublisher[T] = {
    new BasePublisher[T] {
      override def newDefaultSubscriberQueue() = ConsumerQueue(initialValue)
    }
  }
}
