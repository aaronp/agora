package lupin.pub.sequenced

import cats.Functor
import lupin.sub.SubscriberDelegate
import org.reactivestreams.{Publisher, Subscriber}

trait SequencedPublisher[T] extends Publisher[(T, Long)] {
  def subscribeFrom(index: Long, subscriber: Subscriber[_ >: (T, Long)]): Unit

  /**
    * The default is to start subscribing from the first available index
    *
    * @param subscriber
    */
  override def subscribe(subscriber: Subscriber[_ >: (T, Long)]) = subscribeFrom(firstIndex, subscriber)

  /** @return the first index available to read from, or -1 if none
    */
  def firstIndex(): Long

  /**
    * @return the most-recently written index
    */
  def latestIndex(): Option[Long]

  /**
    * Convenience to provide just the data values w/o the indices
    *
    * @return a publisher of the data w/o the indices
    */
  final def valuesPublisher(): Publisher[T] = {
    import lupin.implicits._
    sequencePublisher.map(_._1)
  }

  /**
    * Required so the more specific SequencedPublisher Functor/FlatMap is not chosen
    * @return
    */
  final def sequencePublisher(): Publisher[(T, Long)] = this
}

object SequencedPublisher {

  implicit object SequencedPublisherFunctor extends Functor[SequencedPublisher] {
    override def map[A, B](underlying: SequencedPublisher[A])(f: A => B): SequencedPublisher[B] = {
      new SequencedPublisher[B] {
        override def subscribeFrom(index: Long, subscriber: Subscriber[_ >: (B, Long)]): Unit = {
          underlying.subscribeFrom(
            index,
            new SubscriberDelegate[(A, Long)](subscriber) {
              override def onNext(value: (A, Long)): Unit = {
                val newValue: (B, Long) = (f(value._1), value._2)
                subscriber.onNext(newValue)
              }
            }
          )
        }

        override def firstIndex(): Long = underlying.firstIndex()

        override def latestIndex(): Option[Long] = underlying.latestIndex()
      }
    }
  }

}
