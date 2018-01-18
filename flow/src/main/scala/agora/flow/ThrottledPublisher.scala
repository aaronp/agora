package agora.flow

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

/**
  * Wraps an underlying publisher WHICH IS ONLY ASSUMED TO BE SUBSCRIBED TO ONCE.
  *
  * Any more than 1 subscription per ThrottledPublisher use is an error.
  *
  * It ensures we only allow as many requested as what both the subscription AND what's specified by 'allowRequested'
  * say.
  *
  * @param underlying the underlying publisher
  * @tparam T
  */
class ThrottledPublisher[T](underlying: Publisher[T]) extends Publisher[T] with StrictLogging {
  self =>
  private var singleSubscription: Option[ThrottledPublisher.ThrottledSubscriber[T]] = None

  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    require(singleSubscription.isEmpty, "A throttled publisher is intended for a single use")
    val wrapped: ThrottledPublisher.ThrottledSubscriber[T] = ThrottledPublisher.ThrottledSubscriber[T](s, self)
    singleSubscription = Option(wrapped)
    underlying.subscribe(wrapped)
  }

  /**
    * @param n the number to request
    * @return Long.MinValue if nothing is yet subscribed
    */
  def allowRequested(n: Long): Long = singleSubscription.fold(Long.MinValue) { s =>
    s.allowRequested(n)
  }

  def cancel(): Boolean = singleSubscription.fold(false) { s =>
    s.cancel()
    singleSubscription = None
    true
  }

  def allowed(): Long = singleSubscription.fold(Long.MinValue)(_.allowed())

  def requested(): Long = singleSubscription.fold(Long.MinValue)(_.requested())
}

object ThrottledPublisher {

  private case class ThrottledSubscriber[T](underlying: Subscriber[_ >: T], publisher: ThrottledPublisher[T])
      extends Subscriber[T]
      with Subscription
      with StrictLogging {
    private var subscriptionOpt    = Option.empty[Subscription]
    private val userRequested      = new AtomicLong(0)
    private val throttledRequested = new AtomicLong(0)

    def allowed() = throttledRequested.get()

    def requested() = userRequested.get()

    private def subscription = subscriptionOpt.getOrElse(sys.error("subscription has not yet been set"))

    override def onError(t: Throwable): Unit = underlying.onError(t)

    override def onComplete(): Unit = underlying.onComplete()

    override def onNext(t: T): Unit = underlying.onNext(t)

    override def onSubscribe(s: Subscription): Unit = {
      require(subscriptionOpt.isEmpty, "This subscriber has already been used")
      subscriptionOpt = Option(s)
      underlying.onSubscribe(this)
    }

    override def cancel(): Unit = {
      subscriptionOpt.foreach(_.cancel())
    }

    /**
      * Here is where the throttle is applied - allow 'n' elements to pass
      *
      * @param n the amount to allow
      * @return the number requested
      */
    def allowRequested(n: Long): Long = {
      throttledRequest(userRequested.get, throttledRequested.addAndGet(n))
    }

    /**
      * Here is where we apply the throttle - just request the minimum of what the user requested
      * and what we've throttled via 'allowRequested'
      *
      * @param n the n amount from user-requested
      */
    override def request(n: Long): Unit = {
      throttledRequest(userRequested.addAndGet(n), throttledRequested.get)
    }

    private def throttledRequest(userN: Long, throttleN: Long): Long = {
      val canTake = userN.min(throttleN).max(0)

      logger.debug(s"throttledRequest($userN, $throttleN) --> $canTake")

      if (canTake > 0) {
        userRequested.addAndGet(-canTake)
        throttledRequested.addAndGet(-canTake)
        subscription.request(canTake)
      }
      canTake
    }
  }

}
