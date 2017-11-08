package agora.api.exchange.observer

import agora.api.JobId
import agora.api.exchange._
import agora.api.json.JsonDelta
import agora.api.time.now
import agora.api.worker.SubscriptionKey

/**
  * Something which can observe the changes to an [[Exchange]].
  *
  * See [[ExchangeNotificationMessage]] for a list of all notifications.
  *
  * NOTE: Implementations will need to override either 'onEvent' or the specific 'onXXX' methods.
  * They are both provided for convenience, but failure to override either 'onEvent' or __all__ of the remaining 'onXXX'
  * methods will result in a stack overflow at runtime(!)
  *
  */
trait ExchangeObserver {

  def onSubscriptionRequestCountChanged(msg: OnSubscriptionRequestCountChanged): Unit = onEvent(msg)

  final def onSubscriptionRequestCountChanged(subscriptionRequestId: SubscriptionKey, requestCountBefore: Int, requestCountAfter: Int): Unit = {
    onSubscriptionRequestCountChanged(OnSubscriptionRequestCountChanged(now(), subscriptionRequestId, requestCountBefore, requestCountAfter))
  }

  def onSubscriptionUpdated(msg: OnSubscriptionUpdated): Unit = onEvent(msg)

  final def onSubscriptionUpdated(id: SubscriptionKey, subscription: WorkSubscription, remaining: Int, delta: JsonDelta): Unit = {
    onSubscriptionUpdated(OnSubscriptionUpdated(now(), delta, Candidate(id, subscription, remaining)))
  }

  def onJobSubmitted(msg: OnJobSubmitted): Unit = onEvent(msg)

  final def onJobSubmitted(job: SubmitJob): Unit = onJobSubmitted(OnJobSubmitted(now(), job))

  def onSubscriptionCreated(msg: OnSubscriptionCreated): Unit = onEvent(msg)

  final def onSubscriptionCreated(id: SubscriptionKey, subscription: WorkSubscription, remaining: Int): Unit = {
    onSubscriptionCreated(OnSubscriptionCreated(now(), Candidate(id, subscription, remaining)))
  }

  def onJobsCancelled(msg: OnJobsCancelled): Unit  = onEvent(msg)
  final def onJobsCancelled(ids: Set[JobId]): Unit = onJobsCancelled(OnJobsCancelled(now(), ids))

  def onSubscriptionsCancelled(msg: OnSubscriptionsCancelled): Unit = onEvent(msg)
  final def onSubscriptionsCancelled(cancelledSubscriptionKeys: Set[SubscriptionKey]): Unit = {
    onSubscriptionsCancelled(OnSubscriptionsCancelled(now(), cancelledSubscriptionKeys))
  }

  def onMatch(msg: OnMatch): Unit = onEvent(msg)

  def onStateOfTheWorld(msg: OnStateOfTheWorld): Unit = onEvent(msg)

  /**
    * General handler for all events
    *
    * @param event
    */
  def onEvent(event: ExchangeNotificationMessage): Unit = {
    event match {
      case msg: OnSubscriptionRequestCountChanged => onSubscriptionRequestCountChanged(msg)
      case msg: OnSubscriptionUpdated             => onSubscriptionUpdated(msg)
      case msg: OnJobSubmitted                    => onJobSubmitted(msg)
      case msg: OnSubscriptionCreated             => onSubscriptionCreated(msg)
      case msg: OnJobsCancelled                   => onJobsCancelled(msg)
      case msg: OnSubscriptionsCancelled          => onSubscriptionsCancelled(msg)
      case msg: OnMatch                           => onMatch(msg)
      case msg: OnStateOfTheWorld                 => onStateOfTheWorld(msg)
    }
  }

}

object ExchangeObserver {

  def apply() = ExchangeObserverDelegate()

  object NoOp extends ExchangeObserver {
    override def onEvent(event: ExchangeNotificationMessage): Unit = {}
  }

}
