package lupin.pub.sequenced

import cats.Functor
import lupin.pub.PublisherImplicits
import lupin.sub.SubscriberDelegate
import org.reactivestreams.{Publisher, Subscriber}

trait SequencedPublisher[T] extends Publisher[(Long, T)] {
  def subscribeFrom(index: Long, subscriber: Subscriber[_ >: (Long, T)]): Unit

  /**
    * The default is to start subscribing from the first available index
    *
    * @param subscriber
    */
  override def subscribe(subscriber: Subscriber[_ >: (Long, T)]) = subscribeFrom(firstIndex, subscriber)

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
    asRichPublisher(sequencePublisher).map(_._2)
  }

  /**
    * Required so the more specific SequencedPublisher Functor/FlatMap is not chosen
    * @return
    */
  final def sequencePublisher(): Publisher[(Long, T)] = this
}

object SequencedPublisher {

  implicit object SequencedPublisherFunctor extends Functor[SequencedPublisher] {
    override def map[A, B](underlying: SequencedPublisher[A])(f: A => B): SequencedPublisher[B] = {
      new SequencedPublisher[B] {
        override def subscribeFrom(index: Long, subscriber: Subscriber[_ >: (Long, B)]): Unit = {
          underlying.subscribeFrom(index, new SubscriberDelegate[(Long, A)](subscriber) {
            override def onNext(value: (Long, A)): Unit = {
              val newValue = (value._1, f(value._2))
              subscriber.onNext(newValue)
            }
          })
        }

        override def firstIndex(): Long = underlying.firstIndex()

        override def latestIndex(): Option[Long] = underlying.latestIndex()
      }
    }
  }

}