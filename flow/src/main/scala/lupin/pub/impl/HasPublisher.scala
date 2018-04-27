package lupin.pub.impl

import org.reactivestreams.{Publisher, Subscriber}

/**
  * Allows something which has a publisher to act like a publisher
  * @tparam T
  */
trait HasPublisher[T] extends Publisher[T] {

  protected def underlyingPublisher: Publisher[T]

  override def subscribe(s: Subscriber[_ >: T]) = underlyingPublisher.subscribe(s)

}
