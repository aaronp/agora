package jabroni.api
package `match`

case class MatchDetails(matchId: MatchId, subscriptionKey: SubscriptionKey, jobId: JobId, remainingItems: Int, matchEpochUTC: Long)

object MatchDetails {
  val MatchIdHeader = "Exchange-Match-ID"
  val SubscriptionKeyHeader = "Exchange-Subscription-ID"
  val JobIdHeader = "Exchange-Job-ID"
  val RemainingItemsHeader = "Exchange-Subscription-Remaining-Items"
  val MatchTimeHeader = "Exchange-Match-Timestamp"
}
