package agora.flow

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Subscriber, Subscription}

/**
  * A base subscriber
  *
  * @tparam T
  */
trait BaseSubscriber[T] extends Subscriber[T] with StrictLogging {

  private var _subscriptionOption: Option[Subscription] = None

  protected var initialRequest = 0L

  private var completed = false

  def subscriptionOption: Option[Subscription] = _subscriptionOption

  def contraMap[A](f: A => T): Subscriber[A] = {
    val self = this
    new DelegateSubscriber[A](self) {
      override def toString     = s"contra-mapped $self"
      override def onNext(t: A) = self.onNext(f(t))
    }
  }

  def subscription() = subscriptionOption.getOrElse(sys.error("Subscription not set"))

  override def onError(err: Throwable): Unit = {
    logger.warn(s"$this on error: $err")
    throw err
  }

  override def onComplete(): Unit = {
    logger.debug(s"$this onComplete")
    completed = true
  }

  def isCompleted() = completed

  def request(n: Long = 1) = subscriptionOption match {
    case None =>
      initialRequest = initialRequest + n
      logger.info(s"$this requesting $n BEFORE subscription received, so will request : $initialRequest when we get notified of our subscription")
    case Some(s) =>
      logger.debug(s"$this requesting $n")
      s.request(n)
  }

  def cancel() = subscriptionOption match {
    case None => logger.info(s"$this trying to cancel before we're even subscribed!")
    case Some(s) =>
      logger.debug(s"$this cancelling")
      s.cancel()
  }

  def isSubscribed() = _subscriptionOption.isDefined

  override def onSubscribe(s: Subscription) = {
    require(_subscriptionOption.isEmpty, s"$this subscriptionOption already set to $subscriptionOption")
    _subscriptionOption = Option(s)
    logger.debug(s"$this onSubscribe($s) with $initialRequest initiallyRequested")
    if (initialRequest > 0) {
      s.request(initialRequest)
      initialRequest = 0
    }
  }
}

object BaseSubscriber {
  def apply[T](initialRequested: Long)(f: (BaseSubscriber[T], T) => Unit): BaseSubscriber[T] = new BaseSubscriber[T] { self =>
    initialRequest = initialRequested
    override def onNext(value: T): Unit = f(self, value)
  }
}
