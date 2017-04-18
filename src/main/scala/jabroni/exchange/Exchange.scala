package jabroni.exchange

import jabroni.api
import jabroni.api.JobId
import jabroni.api.client.{ClientRequest, SubmitJob}
import jabroni.api.worker.{RequestWork, WorkerRequest}

object Exchange {

  class Builder() {
    def build = {
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
        case (job: ClientRequest, state) =>
          val applicableOffers: Stream[(api.WorkRequestId, RequestWork)] = state.workOffers.filter {
            case (_, offer) => job.matches(offer) && offer.matches(job)
          }

          val (selected: Stream[(api.WorkRequestId, RequestWork)], remaining) = job.submissionDetails.selection.select(applicableOffers)

          val updatedAfterSelected = selected.foldLeft(state) {
            case (s, (id, offer: WorkerRequest)) =>

              s.updateOffer(id, 0)
          }

          val newState: ExchangeState = remaining.headOption.fold(updatedAfterSelected) {
            case (id, offer) =>
              updatedAfterSelected.updateOffer(id, offer.itemsRequested)
          }

          newState
      }
    }
  }
}
