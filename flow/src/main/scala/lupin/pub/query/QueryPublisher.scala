package lupin.pub.query

import org.reactivestreams.{Publisher, Subscriber}

/**
  * A publisher which can create subscriptions using some input data
  */
trait QueryPublisher[Q, T] extends Publisher[T] {

  def defaultInput: Q

  def subscribeWith(input: Q, subscriber: Subscriber[_ >: T]): Unit

  /**
    * The default is to start subscribing from the first available index
    *
    * @param subscriber
    */
  override def subscribe(subscriber: Subscriber[_ >: T]) = subscribeWith(defaultInput, subscriber)

}