package jabroni.exec

import akka.http.scaladsl.client.RequestBuilding
import jabroni.api.JobId

object ExecHttp extends RequestBuilding {

  def output(jobId: JobId, file: String) = Get(s"/job?id=$jobId&file=$file")

  def listJobs = Get("/jobs")

  def remove(jobId: JobId)= Delete(s"/job?id=$jobId")
}
