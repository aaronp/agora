package lupin.pub.query

import lupin.example.IndexSelection
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext

/**
  * A publisher which can create subscriptions using some input data
  */
trait QueryPublisher[Q, T] extends Publisher[T] {

  protected def defaultInput: Q

  def subscribeWith(input: Q, subscriber: Subscriber[_ >: T]): Unit

  /**
    * The default is to start subscribing from the first available index
    *
    * @param subscriber
    */
  override def subscribe(subscriber: Subscriber[_ >: T]) = subscribeWith(defaultInput, subscriber)

}

object QueryPublisher {

  def apply[T: Ordering](defaultInput: IndexSelection)(implicit execContext: ExecutionContext): Indexer[T] = {
    new Indexer[T](defaultInput)
  }

}
