package agora.api.streams

import cats.Semigroup

/**
  * A publishing skeleton which exposes a 'publish' to push elements to subscribers.
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



    def apply[F[_], T](newQueueArgs: F[T])(implicit asQueue: AsConsumerQueue[F]) = {
      new BasePublisher[T] {
        override def newDefaultSubscriberQueue() = asQueue.newQueue(newQueueArgs)
      }
    }

    def apply[T](maxCapacity: Int) = {
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
    def apply[T: Semigroup](initialValue: Option[T] = None) = {
      new BasePublisher[T] {
        override def newDefaultSubscriberQueue() = ConsumerQueue(initialValue)
      }
    }
}
