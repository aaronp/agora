package jabroni.exec

import akka.http.scaladsl.client.RequestBuilding
import jabroni.api.JobId

object ExecHttp extends RequestBuilding {

  def output(jobId: JobId, file: String) = Get(s"/rest/job?id=$jobId&file=$file")

  def listJobs = Get("/rest/jobs")

  def remove(jobId: JobId)= Delete(s"/rest/job?id=$jobId")
}
