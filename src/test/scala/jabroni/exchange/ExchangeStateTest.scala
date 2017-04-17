package jabroni.exchange

import jabroni.api
import jabroni.api.{JobId, WorkRequestId}
import jabroni.api.client.{ClientRequest, SubmitJob}
import jabroni.api.exchange.Matcher
import jabroni.api.worker.RequestWork
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
        case (job, state) =>
          val j: SubmitJob = job
          val s: ExchangeState = state
          val applicableOffers: Stream[(api.WorkRequestId, RequestWork)] = state.workOffers.filter {
            case (_, offer) => j.matches(offer) && offer.matches(j)
          }

          val chosen: Seq[(api.WorkRequestId, RequestWork)] = job.submissionDetails.matchMode.select(applicableOffers)


          ???
      }
    }
  }
}
