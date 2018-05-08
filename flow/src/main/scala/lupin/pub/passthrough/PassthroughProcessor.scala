package lupin.pub.passthrough

import lupin.pub.FIFO
import org.reactivestreams.{Processor, Publisher, Subscriber}

import scala.concurrent.ExecutionContext

/**
  * A publisher which relies on its subscribers to enqueue all messages -- there is no history/state kept by
  * the publisher, but rather each subscription simply begins getting values from the moment it subscribes.
  *
  *
  * NOTICE: because of this, one fast subscription requesting more elements will end up pulling more elements through
  * other subscriptions which haven't. For this reason, this publisher can only ever go as "fast" as the slowest
  * subscriber. This is not an issue when there is only one, but something to be mindful of it is used w/ multiple
  * subscribers(!)
  *
  */
trait PassthroughProcessor[T] extends PassthroughPublisher[T] with Subscriber[T] with Processor[T] {

  protected def makeDefaultQueue(): FIFO[Option[T]]

  override def subscribe(subscriber: Subscriber[_ >: T]): Unit = subscribe(subscriber, makeDefaultQueue())

  def subscribe(subscriber: Subscriber[_ >: T], queue: FIFO[Option[T]]): Unit
}

object PassthroughProcessor{
  def apply[T](makeDefaultQueue: () => FIFO[Option[T]])(implicit execContext: ExecutionContext): PassthroughProcessor[T] = {
    new PassthroughProcessorInstance[T](makeDefaultQueue)
  }
}
