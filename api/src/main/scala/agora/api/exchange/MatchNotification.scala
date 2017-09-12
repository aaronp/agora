package agora.api.exchange

import agora.api.JobId
import agora.api.worker.CandidateSelection

case class MatchNotification(jobId: JobId, job: SubmitJob, chosen: CandidateSelection)
