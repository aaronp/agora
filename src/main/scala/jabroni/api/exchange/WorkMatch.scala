package jabroni.api
package exchange

import java.time.ZonedDateTime

import jabroni.api.client.SubmitJob
import jabroni.api.worker.RequestWork

/**
  * Represents
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
