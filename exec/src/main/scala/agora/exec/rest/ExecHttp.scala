package agora.exec.rest

import agora.api.JobId
import agora.rest.CommonRequestBuilding

object ExecHttp extends CommonRequestBuilding {

  def output(jobId: JobId, file: String) = Get(s"/rest/job?id=$jobId&file=$file").withCommonHeaders

  def listJobs = Get("/rest/jobs").withCommonHeaders

  def remove(jobId: JobId) = Delete(s"/rest/job?id=$jobId")
}
