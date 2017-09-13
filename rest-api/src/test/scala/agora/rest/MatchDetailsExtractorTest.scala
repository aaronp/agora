package agora.rest

import java.time.LocalDateTime

import agora.api.BaseSpec
import agora.api.`match`.MatchDetails
import akka.http.scaladsl.model.HttpRequest

class MatchDetailsExtractorTest extends BaseSpec {

  "MatchDetailsExtractor.headersFor and unapply" should {
    "extract match details from request headers" in {

      val details = MatchDetails(
        matchId = "it was the best of times...",
        subscriptionKey = "the subscription key",
        jobId = "the job id",
        remainingItems = 123,
        matchedAt = LocalDateTime.now().withNano(0)
      )

      val headers = MatchDetailsExtractor.headersFor(details)
      val req     = HttpRequest().withHeaders(headers)
      MatchDetailsExtractor.unapply(req) shouldBe Some(details)
    }
  }

}
