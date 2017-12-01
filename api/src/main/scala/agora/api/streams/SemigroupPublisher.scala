package agora.api.streams

import cats.Semigroup
import cats.syntax.SemigroupSyntax
import cats.syntax.option._
import org.reactivestreams.Subscriber

/**
  * A publisher who will publish the state-of-the world for the first message on a new subscription
  *
  * @param newQueue a means to create an underlying queue for subsriptions
  * @tparam T
  */
class SemigroupPublisher[T: Semigroup](newQueue: () => ConsumerQueue[T]) extends BasePublisher[T](newQueue) with SemigroupSyntax {

  private[this] object SowLock

  private[this] var stateOfTheWorld: Option[T] = None

  override protected def newSubscription(subscriber: Subscriber[_ >: T]): BasePublisher.BasePublisherSubscription[T] = {
    val s = super.newSubscription(subscriber)
    SowLock.synchronized {
      stateOfTheWorld.foreach(s.onElement)
    }
    s
  }

  override def publish(elem: T): Unit = {
    SowLock.synchronized {
      val newSOW = stateOfTheWorld.fold(elem)(_.combine(elem))
      stateOfTheWorld = newSOW.some
    }

    super.publish(elem)
  }

}

object SemigroupPublisher {
  def apply[T: Semigroup](newQueue: () => ConsumerQueue[T]) = new SemigroupPublisher[T](newQueue)

  def apply[T: Semigroup](maxCapacity: Int) = {
    new SemigroupPublisher[T](() => ConsumerQueue(maxCapacity))
  }

  def apply[T: Semigroup](initialValue: Option[T] = None) = {
    new SemigroupPublisher[T](() => ConsumerQueue(initialValue))
  }
}
