package agora.api.exchange

import agora.api.worker.SubscriptionKey

case class Candidate(subscriptionKey: SubscriptionKey, subscription: WorkSubscription, remaining: Int) {
  require(remaining >= 0)
}
