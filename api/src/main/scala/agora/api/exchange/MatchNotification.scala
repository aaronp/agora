package agora.api.exchange

import agora.api.JobId
import agora.api.worker.CandidateSelection

@deprecated("use OnMatch in ExchangeObserver", "v0.4...")
case class MatchNotification(jobId: JobId, job: SubmitJob, chosen: CandidateSelection)
