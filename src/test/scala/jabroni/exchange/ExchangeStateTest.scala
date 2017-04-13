package jabroni.exchange

import jabroni.api.JobId
import jabroni.api.client.SubmitJob
import jabroni.api.exchange.Matcher
import org.scalatest.{Matchers, WordSpec}

class ExchangeStateTest extends WordSpec
  with Matchers
  with Matcher.LowPriorityImplicits {

  "ExchangeState.notify" should {
    "notify on matches" in {
      val onWork = ExchangeState.onWorkOffer {
        case (offer, state) =>

          val applicableJobs: Stream[(JobId, SubmitJob)] = state.jobs.filter {
            case (_, job) => job.matches(offer) && offer.matches(job)
          }


          val (matching, remaining) = applicableJobs.splitAt(offer.itemsRequested)

          if (matching.isEmpty) {
            state
          } else {
            ???
          }
      }.onSubmission {
        case (job, state) => ???
      }
    }
  }
}
