package agora.api.exchange

import agora.api.JobId
import agora.api.worker.CandidateSelection

case class MatchNotification(id: JobId, job: SubmitJob, chosen: CandidateSelection)
