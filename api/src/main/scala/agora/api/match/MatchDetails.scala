package agora.api
package `match`

import java.time.{LocalDateTime, ZoneOffset}

case class MatchDetails(matchId: MatchId,
                        subscriptionKey: SubscriptionKey,
                        jobId: JobId,
                        remainingItems: Int,
                        matchedAtEpochSecondUTC: Long)

object MatchDetails {

  def apply(matchId: MatchId,
            subscriptionKey: SubscriptionKey,
            jobId: JobId,
            remainingItems: Int,
            matchedAtEpochUTC: LocalDateTime) = {
    new MatchDetails(matchId, subscriptionKey, jobId, remainingItems, matchedAtEpochUTC.toEpochSecond(ZoneOffset.UTC))
  }

  val empty = MatchDetails("", "", "", 0, 0)

  val MatchIdHeader         = "x-Exchange-Match-ID"
  val SubscriptionKeyHeader = "x-Exchange-Subscription-ID"
  val JobIdHeader           = "x-Exchange-Job-ID"
  val RemainingItemsHeader  = "x-Exchange-Subscription-Remaining-Items"
  val MatchTimeHeader       = "x-Exchange-Match-Timestamp"
}
