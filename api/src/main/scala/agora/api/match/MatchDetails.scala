package agora.api
package `match`

import java.time.LocalDateTime

case class MatchDetails(matchId: MatchId, subscriptionKey: SubscriptionKey, jobId: JobId, remainingItems: Int, matchedAt: LocalDateTime)

object MatchDetails {
  val empty = MatchDetails("", "", "", 0, LocalDateTime.MIN)

  val MatchIdHeader         = "x-Exchange-Match-ID"
  val SubscriptionKeyHeader = "x-Exchange-Subscription-ID"
  val JobIdHeader           = "x-Exchange-Job-ID"
  val RemainingItemsHeader  = "x-Exchange-Subscription-Remaining-Items"
  val MatchTimeHeader       = "x-Exchange-Match-Timestamp"
}
