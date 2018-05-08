package lupin.pub.passthrough

import lupin.pub.FIFO
import lupin.sub.BaseSubscriber
import org.reactivestreams.Subscriber

import scala.concurrent.ExecutionContext


/**
  * NOTE: some FIFO impls may block when enqueueing, so we should be mindful of that when pushing to them.
  *
  * Also, one fast subscription requesting more elements will end up pulling more elements through others which
  * haven't.
  *
  */
class PassthroughProcessorInstance[T](newQueue: () => FIFO[Option[T]])(implicit execContext: ExecutionContext) extends BaseSubscriber[T] with PassthroughProcessor[T] {

  private object SubscribersByIdLock

  private var subscribersById = Map[Int, PassthroughSubscription[T]]()
  private var maxRequestedValueFromASubscriber = 0L
  private var receivedError = Option.empty[Throwable]
  private var completed = false

  private object MaxRequestedValueFromASubscriberLock

  override protected def makeDefaultQueue = newQueue()

  override def subscribe(s: Subscriber[_ >: T], queue: FIFO[Option[T]]): Unit = {

    val newSub = SubscribersByIdLock.synchronized {
      val nextId = if (subscribersById.isEmpty) {
        0
      } else {
        subscribersById.keySet.max + 1
      }

      val subscription = new PassthroughSubscription[T](nextId, queue, this, s)
      subscribersById = subscribersById.updated(nextId, subscription)
      subscription
    }

    // start pulling in the queue
    execContext.execute(newSub)
    s.onSubscribe(newSub)
    receivedError.foreach(newSub.markOnError)
    if (completed) {
      newSub.queue.enqueue(None)
    }
  }

  override def onNext(t: T): Unit = {
    if (t == null) {
      onError(new Exception("Received null onNext"))
    } else {
      enqueue(Some(t))
    }
  }

  private def enqueue(t: Option[T]) = {
    subscribersById.values.foreach { sub =>
      if (!sub.queue.enqueue(t)) {
        sub.markOnError(new Exception(s"Couldn't enqueue $t"))
      }
    }
  }

  override def onError(t: Throwable): Unit = {
    receivedError = receivedError.orElse(Option(t))
    val subscribers = SubscribersByIdLock.synchronized {
      val oldSubsribers = subscribersById.values
      subscribersById = Map.empty
      oldSubsribers
    }
    subscribers.foreach(_.markOnError(t))
  }

  override def onComplete(): Unit = {
    enqueue(None)
    completed = true
  }

  private[passthrough] def remove(subscriberId: Int): Unit = {
    SubscribersByIdLock.synchronized {
      subscribersById = subscribersById - subscriberId
    }
  }


  protected[passthrough] def onSubscriptionRequesting(subscriberId: Int, previouslyRequested: Long, newValue: Long): Long = {
    val nrToRequest = MaxRequestedValueFromASubscriberLock.synchronized {
      if (maxRequestedValueFromASubscriber < newValue) {
        maxRequestedValueFromASubscriber = newValue
        newValue - maxRequestedValueFromASubscriber
      } else {
        0
      }
    }

    // propagate our subscription
    if (nrToRequest > 0) {
      request(nrToRequest)
    }
    nrToRequest
  }
}