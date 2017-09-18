package agora.rest

import java.time.{LocalDateTime, ZoneOffset}

import akka.http.scaladsl.model.{HttpHeader, HttpMessage}
import agora.api.`match`.MatchDetails
import agora.api.`match`.MatchDetails._
import implicits._

object MatchDetailsExtractor {

  def headersFor(matchDetails: MatchDetails): List[HttpHeader] = {
    List(
      //EncodingHeaders,
      MatchIdHeader.asHeader(matchDetails.matchId),
      SubscriptionKeyHeader.asHeader(matchDetails.subscriptionKey),
      JobIdHeader.asHeader(matchDetails.jobId),
      RemainingItemsHeader.asHeader(matchDetails.remainingItems.toString),
      MatchTimeHeader.asHeader(matchDetails.matchedAtEpochSecondUTC.toString)
    )
  }

  def unapply(req: HttpMessage): Option[MatchDetails] = {
    def get(key: String): Option[String] = Option(req.getHeader(key).orElse(null)).map(_.value)

    for {
      matchId   <- get(MatchIdHeader)
      key       <- get(SubscriptionKeyHeader)
      job       <- get(JobIdHeader)
      remaining <- get(RemainingItemsHeader)
      time      <- get(MatchTimeHeader)
    } yield {
      MatchDetails(matchId, key, job, remaining.toInt, LocalDateTime.ofEpochSecond(time.toLong, 0, ZoneOffset.UTC))
    }
  }

}
