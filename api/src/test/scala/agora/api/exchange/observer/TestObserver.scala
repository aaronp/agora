package agora.api.exchange.observer

import agora.api.exchange.Candidate

/**
  * An [[ExchangeObserver]] which just writes down the events as they come through
  */
class TestObserver extends ExchangeObserver {
  var events = List[ExchangeNotificationMessage]()

  override def onEvent(event: ExchangeNotificationMessage): Unit = {
    events = event :: events
  }

  def size = events.size

  def isEmpty = events.isEmpty

  /** @return the most recent [[OnSubscriptionCreated]]
    */
  def lastCreated(): Option[Candidate] = {
    events.collectFirst {
      case msg: OnSubscriptionCreated => msg.subscription
    }
  }

  /** @return the most recent [[OnSubscriptionUpdated]]
    */
  def lastUpdated(): Option[OnSubscriptionUpdated] = {
    events.collectFirst {
      case update: OnSubscriptionUpdated => update
    }
  }
  def lastUpdatedSubscription(): Option[Candidate] = lastUpdated().map(_.subscription)
}
