package agora.exec.workspace

import agora.exec.model.RunProcess
import concurrent.duration._
import scala.concurrent.duration.FiniteDuration

/**
  * A [[RunProcess]] may depend on some files being uploaded/available.
  * UploadDependencies represents those dependencies so that a [[agora.exec.workspace.WorkspaceClient]] can
  * wait on those being available, allowing the uploading of files to be asynchronous/separate from running
  * commands which operate on those files
  *
  * @param timeoutInMillis
  */
case class UploadDependencies(workspace: WorkspaceId, dependsOnFiles: Set[String], timeoutInMillis: Long) {
  require(dependsOnFiles.nonEmpty, s"A dependency for $workspace was created without any file dependencies")
  def timeout = timeoutInMillis.millis

}
