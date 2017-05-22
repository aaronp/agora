package jabroni.rest

import akka.http.scaladsl.model.{HttpHeader, HttpMessage}
import jabroni.api.`match`.MatchDetails
import jabroni.api.`match`.MatchDetails._

object MatchDetailsExtractor {

  import CommonRequestBuilding._

  def headersFor(matchDetails: MatchDetails): List[HttpHeader] = {
    import implicits._
    List(
      //EncodingHeaders,
      MatchIdHeader.asHeader(matchDetails.matchId),
      SubscriptionKeyHeader.asHeader(matchDetails.subscriptionKey),
      JobIdHeader.asHeader(matchDetails.jobId),
      RemainingItemsHeader.asHeader(matchDetails.remainingItems.toString),
      MatchTimeHeader.asHeader(matchDetails.matchEpochUTC.toString)
    )
  }

  def unapply(req: HttpMessage): Option[MatchDetails] = {
    def get(key: String) = Option(req.getHeader(key).orElse(null)).map(_.value)

    for {
      matchId <- get(MatchIdHeader)
      key <- get(SubscriptionKeyHeader)
      job <- get(JobIdHeader)
      remaining <- get(RemainingItemsHeader)
      time <- get(MatchTimeHeader)
    } yield {
      MatchDetails(matchId, key, job, remaining.toInt, time.toLong)
    }
  }
}
