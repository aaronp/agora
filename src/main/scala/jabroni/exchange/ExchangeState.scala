package jabroni.exchange

import jabroni.api
import jabroni.api.{JobId, WorkRequestId}
import jabroni.api.client.SubmitJob
import jabroni.api.exchange.JobPredicate
import jabroni.api.worker.{RequestWork, WorkerRequest}
import jabroni.api.exchange.SelectionMode._
import jabroni.domain.Take

/**
  * The interchange between work requests and workers asking for work
  */
trait ExchangeState {

  def getSubmission(id: JobId): Option[SubmitJob]

  /**
    * @return the new state and the optionally found submit job
    */
  def removeSubmission(id: JobId): (ExchangeState, Option[SubmitJob])

  def jobs: Stream[(JobId, SubmitJob)]

  def workOffers: Stream[(WorkRequestId, RequestWork)]

  def updateOffer(id: JobId, newItemsRequested: Int): ExchangeState

  def getWorkRequest(id: WorkRequestId): Option[RequestWork]

  def offerWork(id: WorkRequestId, req: RequestWork): ExchangeState

  def submitJob(id: JobId, job: SubmitJob): ExchangeState

}

object ExchangeState {

  //  class DispatchingState(dispatcher: WorkDispatcher, underlying : ExchangeState) extends DelegateState(underlying) {
  //
  //  }
  class DelegateState(underlying: ExchangeState) extends ExchangeState {
    override def getSubmission(id: JobId) = underlying.getSubmission(id)

    override def removeSubmission(id: JobId) = underlying.removeSubmission(id)

    override def jobs = underlying.jobs

    override def workOffers = underlying.workOffers

    override def updateOffer(id: JobId, newItemsRequested: Int) = underlying.updateOffer(id, newItemsRequested)

    override def getWorkRequest(id: WorkRequestId) = underlying.getWorkRequest(id)

    override def offerWork(id: WorkRequestId, req: RequestWork) = underlying.offerWork(id, req)

    override def submitJob(id: JobId, job: SubmitJob) = underlying.submitJob(id, job)
  }


  case class DispatchingState(pendingJobsById: Map[JobId, SubmitJob],
                              pendingWorkRequestsById: Map[WorkRequestId, RequestWork],
                              dispatcher: WorkDispatcher)(implicit m : JobPredicate = JobPredicate()) extends ExchangeState {
    override def getSubmission(id: JobId): Option[SubmitJob] = pendingJobsById.get(id)

    override def jobs = pendingJobsById.toStream

    override def workOffers = pendingWorkRequestsById.toStream

    override def removeSubmission(id: JobId): (ExchangeState, Option[SubmitJob]) = {
      getSubmission(id).fold((this: ExchangeState) -> Option.empty[SubmitJob]) { s =>
        val newState: Map[JobId, SubmitJob] = pendingJobsById - id
        copy(pendingJobsById = newState) -> Option(s)
      }
    }

    override def getWorkRequest(id: WorkRequestId): Option[RequestWork] = pendingWorkRequestsById.get(id)

    override def offerWork(id: WorkRequestId, offer: RequestWork): ExchangeState = {
      val inputState = copy(pendingWorkRequestsById = pendingWorkRequestsById.updated(id, offer))
      val (selections, newState) = handleWorkOffer(inputState, offer, None)
      selections.foreach {
        case (job, sel) => dispatcher.dispatchAll(sel, job)
      }
      newState
    }

    override def submitJob(id: JobId, job: SubmitJob): ExchangeState = {
      val inputState = copy(pendingJobsById = pendingJobsById.updated(id, job))
      val (selections: _root_.jabroni.api.exchange.SelectionMode.Selected, newState) = handleJobSubmission(inputState, job)
      dispatcher.dispatchAll(selections, job)
      newState
    }

    override def updateOffer(id: WorkRequestId, newItemsRequested: Int): ExchangeState = {
      getWorkRequest(id).filter(_.itemsRequested != newItemsRequested).fold(this: ExchangeState) { old =>
        val newWork = if (newItemsRequested == 0) {
          pendingWorkRequestsById - id
        } else {
          pendingWorkRequestsById.updated(id, old.copy(itemsRequested = newItemsRequested))
        }
        copy(pendingWorkRequestsById = newWork)
      }
    }
  }


  private def getState[T]: (T, ExchangeState) => ExchangeState = (_, state: ExchangeState) => state

  //  def apply(): ExchangeState = notify(getState[RequestWork], getState[SubmitJob])
  //  def apply(): ExchangeState = onWorkOffer(getState).onSubmission(getState)

  def apply(dispatcher: WorkDispatcher) : ExchangeState  = new DispatchingState(Map.empty, Map.empty, dispatcher)

  //
  //  /**
  //    * Begins the 'onWorkOffer' ... 'onSubmission' DSL
  //    *
  //    * @param onOffer
  //    * @return
  //    */
  //  def onWorkOffer(onOffer: (RequestWork, ExchangeState) => ExchangeState) = {
  //    new OnWorkOfferWord(onOffer)
  //  }
  //
  //  /**
  //    * Used to expose the 'onSubmission' DSL
  //    *
  //    * @param onWorkOffer
  //    */
  //  class OnWorkOfferWord(onWorkOffer: (RequestWork, ExchangeState) => ExchangeState) {
  //    def onSubmission(onJob: (SubmitJob, ExchangeState) => ExchangeState): ExchangeState = {
  //      new NotifyingState(Map.empty, Map.empty, onWorkOffer, onJob)
  //    }
  //  }


  def handleWorkOffer(state: ExchangeState, offer: RequestWork, priority: Option[Ordering[(JobId, SubmitJob)]])(implicit m : JobPredicate): (List[(SubmitJob, Selected)], ExchangeState) = {

    val applicableJobs: Stream[(JobId, SubmitJob)] = state.jobs.filter {
      case (_, job) => job.matches(offer) && offer.matches(job)
    }

    val prioritizedJobs: Seq[(api.JobId, SubmitJob)] = priority.fold(applicableJobs)(ord => applicableJobs.sorted(ord))

    //  TODO - use 'Take' ... or something that will exit early.
    // typically the first 'handleJobSubmission' will consume the workers slots, so we iterate over all the jobs just
    // to end up sending the first one to the offer
    prioritizedJobs.foldLeft(List[(SubmitJob, Selected)]() -> state) {
      case ((selectionById, stateAcc), (id, job)) =>
        val (selected, newState) = handleJobSubmission(stateAcc, job)
        val newPairs = (job, selected) :: selectionById
        newPairs -> newState
    }
  }

  def handleJobSubmission(state: ExchangeState, job: SubmitJob)(implicit m : JobPredicate): (Selected, ExchangeState) = {

    //
    // find all applicable work offers for this job based on both the job and offer filter criteria
    //
    val applicableOffers: Stream[(api.WorkRequestId, RequestWork)] = state.workOffers.filter {
      case (_, offer) => job.matches(offer) && offer.matches(job)
    }

    //
    // of the found offers, choose ones to match based on the job selection mode
    //
    val (selected, remaining) = job.select(applicableOffers)

    selected -> updateState(state, selected, remaining)
  }

  private def updateState(state: ExchangeState, selected: Selected, remaining: Remaining) = {
    val updatedAfterSelected = selected.foldLeft(state) {
      case (s, (id, offer: WorkerRequest)) => s.updateOffer(id, 0)
    }

    remaining.headOption.fold(updatedAfterSelected) {
      case (id, offer) =>
        updatedAfterSelected.updateOffer(id, offer.itemsRequested)
    }
  }
}