package lupin.pub.passthrough

import com.typesafe.scalalogging.StrictLogging
import lupin.pub.FIFO
import org.reactivestreams.{Subscriber, Subscription}

import scala.util.Try

private[passthrough] class PassthroughSubscription[T](id: Int,
                                                      val queue: FIFO[Option[T]],
                                                      publisher: PassthroughPublisherInstance[T],
                                                      subscriber: Subscriber[_ >: T])
  extends Subscription
    with Runnable
    with StrictLogging {

  @volatile var currentlyRequested = 0L
  @volatile var totalRequested = 0L
  @volatile var completed = false

  private object Lock

  override def run(): Unit = {
    while (!completed) {
      pollLoop()
    }
    logger.debug(s"$id is completed")
  }

  private def pollLoop() = {
    var numToTake = awaitRequested()
    while (numToTake > 0 && !completed && pushNext()) {
      numToTake = numToTake - 1
    }
  }

  private def awaitRequested() = {
    while (currentlyRequested == 0L) {
      logger.debug(s"$id waiting for some elems to be requested")
      Lock.synchronized {
        Lock.wait()
      }
    }
    logger.debug(s"$id has $currentlyRequested requested")
    currentlyRequested
  }

  private def pushNext(): Boolean = {
    queue.pop() match {
      case None =>
        completed = true
        subscriber.onComplete()
        false
      case Some(next) =>
        subscriber.onNext(next)
        true
    }
  }

  override def request(n: Long): Unit = {
    val (previous, newValue) = Lock.synchronized {
      val before = totalRequested
      currentlyRequested = currentlyRequested + n
      totalRequested = totalRequested + n
      Lock.notify()
      before -> totalRequested
    }

    publisher.onSubscriptionRequesting(id, previous, newValue)
  }

  def markOnError(err: Throwable): Unit = {
    completed = true
    publisher.remove(id)
    Try(subscriber.onError(err))
  }

  override def cancel(): Unit = {
    completed = true
    publisher.remove(id)
  }
}
