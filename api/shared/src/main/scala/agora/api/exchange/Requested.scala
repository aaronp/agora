package agora.api.exchange

import agora.api.worker.SubscriptionKey

private[exchange] sealed trait Requested {
  def remaining(state : ExchangeState) : Int
}
object Requested {
  def apply(n : Int) = FixedRequested(n)

  def apply(refs : Set[SubscriptionKey]) : Requested = {
    if (refs.isEmpty) {
      FixedRequested(0)
    } else {
      LinkedRequested(refs)
    }
  }
}

private[exchange] case class FixedRequested(n : Int) extends Requested {
  override def remaining(state : ExchangeState) = n
}

private[exchange] case class LinkedRequested(subscriptions : Set[SubscriptionKey]) extends Requested {
  override def remaining(state : ExchangeState) = {
    subscriptions.map(state.pending).min
  }

//  def pending(state : ExchangeState, seen: Set[SubscriptionKey]) = {
//      subscriptions.map(state.pending)
//  }
}
