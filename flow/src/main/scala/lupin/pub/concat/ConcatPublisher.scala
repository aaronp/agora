package lupin.pub.concat

import lupin.pub.sequenced.DurableProcessor.Args
import lupin.pub.sequenced.{DurableProcessorDao, DurableProcessorInstance}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext

object ConcatPublisher {

  /**
    * Concatenate the given publishers to represent them as a single publisher
    * @param head the first publisher
    * @param tail the second publisher
    * @param ec an execution context used to drive subscriptions
    * @tparam T
    * @return a single publisher which represents the two publishers
    */
  def concat[T](head: Publisher[T], tail: Publisher[T])(implicit ec: ExecutionContext): Publisher[T] = {
    concat(head)(tail.subscribe)
  }

  def concat[T](head: Publisher[T])(subscribeNext: Subscriber[T] => Unit)(implicit ec: ExecutionContext): Publisher[T] = {

    /**
      * Override the processor to change the behaviour of 'onComplete' to
      * instead subscribe to the second publisher
      */
    val dp = new DurableProcessorInstance[T](Args(DurableProcessorDao[T](), true, -1)) {

      private var firstCompleted = false

      override def onSubscribe(s: Subscription): Unit = {
        // allow us to reset the subscription
        super.onSubscribe(s)
      }

      override def onComplete(): Unit = {
        if (firstCompleted) {
          super.onComplete()
        } else {
          firstCompleted = true
          clearSubscription()
          subscribeNext(this)
        }
      }
    }
    head.subscribe(dp)

    dp
  }

}
