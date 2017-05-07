package jabroni.rest

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpHeader, HttpMessage}
import jabroni.api.`match`.MatchDetails
import MatchDetails._

object MatchDetailsExtractor {

  def headersFor(matchDetails: MatchDetails): List[HttpHeader] = {
    import implicits._
    List(
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
