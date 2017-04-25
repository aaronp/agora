package jabroni.api
package exchange

import java.time.ZonedDateTime

import jabroni.api.client.SubmitJob
import jabroni.api.worker.RequestWork

/**
  * Represents the pairing of a job w/ a worker
  *
  * @param workId
  * @param jobId
  * @param submitJob
  * @param workRequest
  */
case class WorkMatch(matchedAt: ZonedDateTime,
                     workId: WorkRequestId,
                     jobId: JobId,
                     submitJob: SubmitJob,
                     workRequest: RequestWork)
