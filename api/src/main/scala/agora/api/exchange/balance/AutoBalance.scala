package agora.api.exchange.balance

import agora.api.json.JPredicate

/**
  * Contains functions to support auto-balancing subscriptions.
  *
  * There are primarily two use-cases:
  *
  * 1) The endpoints are a 'microservice' type, which cooperate in pulling work from an [[agora.api.exchange.Exchange]]
  * in a reactive-streams way.
  *
  * 2) A typical load-balancing scenario, where the endpoints represented by the subscriptions won't just be able to
  * 'take one more' on each work item. This would be the case where it would be difficult (or impossible) to tell a prior
  * how much time/resource a single request would take based on it's parameters. For example, monte-carlo or other genetic
  * algorithms, or cases which involve other downstream systems of unknown reliability.
  *
  * The 'AutoBalance' functions to serve the latter case. It assumes some task-specific data (which may be as simple
  * as a system health update) is periodically updated on the exchange which we could match against.
  *
  * For example, consider some worker subscriptions which subscribe for work, and periodically update their subscriptions
  * with e.g.
  * {{{
  *   "systemLoadPercent" : 85
  * }}}
  *
  * We could then set up some a job which would match subscriptions where both the load percent is over a certain
  * threshold.
  *
  * We could even get really clever and set up a series of subscriptions for different percentages and requested values.
  *
  * For example:
  *
  * {{{
  * subscription1 matches systemLoadPercent > 50 and requested > 3
  * subscription2 matches systemLoadPercent > 75 and requested > 1
  * subscription2 matches systemLoadPercent > 90
  * }}}
  *
  * When any of these subscriptions match, the requesting client is notified of the eligible workers. In this instance,
  * instead of sending the workers work, we simply adjust the requested count on the exchange.
  *
  * In that general way we can handle both cases of over and under utilisation, simply by swapping around the thresholds
  * and 'take' amounts.
  *
  * == Complex Matching Use Cases ==
  * NOTE: In cases where the criteria is more complicated (e.g. we might have some complex combination of memory,
  * number of requested, cores available, etc.), we could chose to submit a job which would match a subset of that
  * criteria, perform some overall calculation, and then in-turn either update the subscription or provide another
  * subscription with a simplified result). In that way users could satisfy even their most complex criteria/use cases.
  *
  * For example, we could submit a 'ComplexHealthCalculator' job to match subscriptions which simply have the
  * following values:
  *
  * 1) system.cores
  * 2) system.availableMemory
  * 3) loadThreshold
  * 4) some.software.supportedVersion
  *
  * That job would be submitted with a 'select all' selection mode. Instead of submitting work to those workers,
  * it would instead calculate something like:
  * {{{
  *   "calculated" : {
  *     "bucket" : "big",
  *     "decisionMadeAt" : "<some timestamp>"
  *   }
  * }}}
  * with which to update each subscription.
  *
  * The actual work jobs would then specify "calculated.bucket eq 'big'" as their match criteria, so the subscriptions
  * would only match jobs once the decision logic has operated on subscription updates.
  *
  * It would even be possible to chain a number of those types of subscriptions to come up with arbitrarily complex work-flows
  * (though caveat emptor and all that).
  *
  * Ya feel me?
  */
object AutoBalance extends App {

  def mkCriteria() = {}

  import agora.api.Implicits._

  val pctn: JPredicate = ("systemLoadPercent" gte 80) and ("requested" gt 1)
  println(pctn.json.spaces2)

  val pctn2: JPredicate = ("systemLoadPercent" lt 50) and ("requested" lt 2)
  println(pctn2.json.spaces2)

}
