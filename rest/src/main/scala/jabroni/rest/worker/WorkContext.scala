package jabroni.rest.worker

import jabroni.api.SubscriptionKey
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.{Exchange, RequestWorkAck, WorkSubscription}

import scala.concurrent.Future

case class WorkContext[T](exchange: Exchange, subscriptionKey: Option[SubscriptionKey], subscription: WorkSubscription, matchDetails: Option[MatchDetails], request: T) {

  def take(n: Int): Option[Future[RequestWorkAck]] = subscriptionKey.map(s => exchange.take(s, n))

  def details = subscription.details
  def path = details.path.get
}
