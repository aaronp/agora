package jabroni.exec.dao

import jabroni.api.JobId
import jabroni.exec.model.{RunProcess, Upload}

trait ExecDao {
  def save(jobId : JobId, run : RunProcess, inputs : List[Upload]) : Unit

  def get(jobId : JobId) : (RunProcess, List[Upload])
}
