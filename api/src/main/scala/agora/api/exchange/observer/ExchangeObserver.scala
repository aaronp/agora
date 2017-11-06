package agora.api.exchange.observer

import agora.api.JobId
import agora.api.exchange._
import agora.api.json.JsonDelta
import agora.api.time.now
import agora.api.worker.{CandidateSelection, SubscriptionKey}

/**
  * Something which can observe the changes to an Exchange
  */
trait ExchangeObserver {

  def onSubscriptionRequestCountChanged(msg: OnSubscriptionRequestCountChanged): Unit = onEvent(msg)

  def onSubscriptionUpdated(msg: OnSubscriptionUpdated): Unit = onEvent(msg)
  def onSubscriptionUpdated(id: SubscriptionKey, subscription: WorkSubscription, remaining: Int, delta: JsonDelta): Unit = {
    onSubscriptionUpdated(OnSubscriptionUpdated(now(), delta, Candidate(id, subscription, remaining)))
  }

  def onJobSubmitted(msg: OnJobSubmitted): Unit = onEvent(msg)
  def onJobSubmitted(job: SubmitJob): Unit      = onJobSubmitted(OnJobSubmitted(now(), job))

  def onSubscriptionCreated(msg: OnSubscriptionCreated): Unit = onEvent(msg)
  def onSubscriptionCreated(id: SubscriptionKey, subscription: WorkSubscription, remaining: Int): Unit = {
    onSubscriptionCreated(OnSubscriptionCreated(now(), Candidate(id, subscription, remaining)))
  }

  def onJobCancelled(msg: OnJobCancelled): Unit = onEvent(msg)

  def onSubscriptionCancelled(msg: OnSubscriptionCancelled): Unit = onEvent(msg)

  def onMatch(msg: OnMatch): Unit                                             = onEvent(msg)
  def onMatch(jobId: JobId, job: SubmitJob, chosen: CandidateSelection): Unit = onMatch(OnMatch(now(), jobId, job, chosen))

  def onStateOfTheWorld(msg: OnStateOfTheWorld): Unit = onEvent(msg)

  def onEvent(event: ExchangeNotificationMessage): Unit = {
    event match {
      case msg: OnSubscriptionRequestCountChanged => onSubscriptionRequestCountChanged(msg)
      case msg: OnSubscriptionUpdated             => onSubscriptionUpdated(msg)
      case msg: OnJobSubmitted                    => onJobSubmitted(msg)
      case msg: OnSubscriptionCreated             => onSubscriptionCreated(msg)
      case msg: OnJobCancelled                    => onJobCancelled(msg)
      case msg: OnSubscriptionCancelled           => onSubscriptionCancelled(msg)
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
