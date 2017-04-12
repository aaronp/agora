package jabroni.api
package client

import scala.concurrent.Future

trait JobScheduler {

  def submitJob(job: SubmitJob): Future[SubmitJobResponse]

  def getSubmission(id: JobId): Future[SubmitJob]

  def cancelJob(id: JobId): Future[Boolean]

  def getWorkersForJob(id: JobId, blockUntilMatched: Boolean): Future[List[WorkerDetails]]
}
