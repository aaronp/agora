package jabroni.api
package `match`

case class MatchDetails(matchId: MatchId, subscriptionKey: SubscriptionKey, jobId: JobId, remainingItems: Int, matchEpochUTC: Long)

object MatchDetails {
  val empty = MatchDetails("", "", "", 0, 0L)

  val MatchIdHeader = "x-Exchange-Match-ID"
  val SubscriptionKeyHeader = "x-Exchange-Subscription-ID"
  val JobIdHeader = "x-Exchange-Job-ID"
  val RemainingItemsHeader = "x-Exchange-Subscription-Remaining-Items"
  val MatchTimeHeader = "x-Exchange-Match-Timestamp"
}
