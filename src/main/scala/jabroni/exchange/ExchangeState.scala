package jabroni.exchange

import jabroni.api.{JobId, WorkRequestId}
import jabroni.api.client.SubmitJob
import jabroni.api.worker.RequestWork

/**
  * The interchange between work requests and workers asking for work
  */
trait ExchangeState {

  def getSubmission(id: JobId): Option[SubmitJob]

  def jobs: Stream[(JobId, SubmitJob)]

  def workOffers: Stream[(WorkRequestId, RequestWork)]

  /**
    * @return the new state and the optionally found submit job
    */
  def removeSubmission(id: JobId): (ExchangeState, Option[SubmitJob])

  def updateOffer(id: JobId, offer: RequestWork): ExchangeState

  def getWorkRequest(id: WorkRequestId): Option[RequestWork]

  def offerWork(id: WorkRequestId, req: RequestWork): ExchangeState

  def submitJob(id: JobId, job: SubmitJob): ExchangeState

}

object ExchangeState {

  case class NotifyingState(pendingJobsById: Map[JobId, SubmitJob],
                            pendingWorkRequestsById: Map[WorkRequestId, RequestWork],
                            onWorker: (RequestWork, ExchangeState) => ExchangeState,
                            onJob: (SubmitJob, ExchangeState) => ExchangeState) extends ExchangeState {
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

    override def offerWork(id: WorkRequestId, req: RequestWork): ExchangeState = {
      val state = new NotifyingState(pendingJobsById, pendingWorkRequestsById.updated(id, req), onWorker, onJob)
      onWorker(req, state)
    }

    override def submitJob(id: JobId, job: SubmitJob): ExchangeState = {
      val state = new NotifyingState(pendingJobsById.updated(id, job), pendingWorkRequestsById, onWorker, onJob)
      onJob(job, state)
    }

    override def updateOffer(id: WorkRequestId, offer: RequestWork): ExchangeState = {
      getWorkRequest(id).fold(this: ExchangeState) { old =>
        offerWork(id, offer)
      }
    }
  }

  private def getState[T]: (T, ExchangeState) => ExchangeState = (_, state: ExchangeState) => state

  //  def apply(): ExchangeState = notify(getState[RequestWork], getState[SubmitJob])
  def apply(): ExchangeState = onWorkOffer(getState).onSubmission(getState)

  def onWorkOffer(onOffer: (RequestWork, ExchangeState) => ExchangeState) = {
    new OnWorkOfferWord(onOffer)
  }

  class OnWorkOfferWord(onWorkOffer: (RequestWork, ExchangeState) => ExchangeState) {
    def onSubmission(onJob: (SubmitJob, ExchangeState) => ExchangeState): ExchangeState = {
      new NotifyingState(Map.empty, Map.empty, onWorkOffer, onJob)
    }
  }

}