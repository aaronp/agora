package agora.api.exchange.observer

import agora.api.exchange.Candidate

/**
  * An [[ExchangeObserver]] which just writes down the events as they come through
  */
class TestObserver extends ExchangeObserver {
  var events = List[ExchangeNotificationMessage]()

  def eventsInTheOrderTheyWereReceived = events.reverse

  override def onEvent(event: ExchangeNotificationMessage): Unit = {
    println("on Event " + event)
    events = event :: events
  }

  def size = events.size

  def isEmpty = events.isEmpty

  /** @return the most recent [[OnSubscriptionCreated]]
    */
  def lastCreated(): Option[Candidate] = {
    events.collectFirst {
      case msg: OnSubscriptionCreated => msg.subscriptionCreated
    }
  }

  def lastCancelledJob(): Option[OnJobsCancelled] = {
    events.collectFirst {
      case msg: OnJobsCancelled => msg
    }
  }

  def lastCancelledSubscription(): Option[OnSubscriptionsCancelled] = {
    events.collectFirst {
      case msg: OnSubscriptionsCancelled => msg
    }
  }

  def lastMatch(): Option[OnMatch] = {
    events.collectFirst {
      case msg: OnMatch => msg
    }
  }

  /** @return the most recent [[OnSubscriptionUpdated]]
    */
  def lastUpdated(): Option[OnSubscriptionUpdated] = {
    events.collectFirst {
      case msg: OnSubscriptionUpdated => msg
    }
  }

  /** @return the most recent [[OnSubscriptionRequestCountChanged]]
    */
  def lastRequestCountChanged(): Option[OnSubscriptionRequestCountChanged] = {
    events.collectFirst {
      case msg: OnSubscriptionRequestCountChanged => msg
    }
  }

  /** @return the most recent [[OnJobSubmitted]]
    */
  def lastSubmitted(): Option[OnJobSubmitted] = {
    events.collectFirst {
      case msg: OnJobSubmitted => msg
    }
  }

  def stateOfTheWorld() = {
    val list: List[OnStateOfTheWorld] = events.collect {
      case sow: OnStateOfTheWorld => sow
    }
    list.ensuring(_.size < 2, "multiple OnStateOfTheWorld messages received").headOption
  }

  def lastUpdatedSubscription(): Option[Candidate] = lastUpdated().map(_.subscription)
}
