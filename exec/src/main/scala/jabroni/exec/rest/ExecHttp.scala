package jabroni.exec.rest

import jabroni.api.JobId
import jabroni.rest.CommonRequestBuilding

object ExecHttp extends CommonRequestBuilding {

  def output(jobId: JobId, file: String) = Get(s"/rest/job?id=$jobId&file=$file").withCommonHeaders

  def listJobs = Get("/rest/jobs").withCommonHeaders

  def remove(jobId: JobId)= Delete(s"/rest/job?id=$jobId")
}
