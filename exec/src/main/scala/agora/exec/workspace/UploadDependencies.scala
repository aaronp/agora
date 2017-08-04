package agora.exec.workspace

import agora.exec.model.RunProcess

import scala.concurrent.duration._

/**
  * A [[RunProcess]] may depend on some files being uploaded/available.
  * UploadDependencies represents those dependencies so that a [[agora.exec.workspace.WorkspaceClient]] can
  * wait on those being available, allowing the uploading of files to be asynchronous/separate from running
  * commands which operate on those files
  *
  * @param workspace       the workspace in which the dependencies are expected to be uploaded. To.
  * @param dependsOnFiles  the filenames expected to be uploaded
  * @param timeoutInMillis the time to wait for the dependencies to become available
  */
case class UploadDependencies(workspace: WorkspaceId, dependsOnFiles: Set[String], timeoutInMillis: Long) {
  require(dependsOnFiles.nonEmpty, s"A dependency for $workspace was created without any file dependencies")

  def timeout = timeoutInMillis.millis

}
