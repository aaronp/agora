package agora.exec.workspace

import agora.exec.model.RunProcess

import scala.concurrent.duration._

/**
  * A [[RunProcess]] may depend on some files being uploaded/available.
  *
  * UploadDependencies represents those dependencies so that a [[agora.exec.workspace.WorkspaceClient]] can
  * wait on those being available, allowing the uploading of files to be asynchronous/separate from running
  * commands which operate on those files.
  *
  * <div>
  * <h2>A word about 'awaitFlushedOutput'</h2>
  *
  * In issue#1 logged on Github, we put in belt-and-braces check that a '.<fileName>.metadata' marker file
  * is created when a job completes which contains the expected size of <fileName>.
  *
  * We can produce that metadata file by tracking the output of a job or knowing the size of an uploaded file.
  *
  * That mechanism doesn't hold, however, if the process's output doesn't match the bytes produced by the job's output
  * itself. e.g., if we execute "cp fileA fileB". In that case fileB is created, but we can't inspect a special meaning
  * from "cp", knowing that it will produce "fileB" of a certain size.
  *
  * For that case, we can specify in a our 'UploadDependencies' that 'awaitFlushedOutput' should be false, as we won't
  * ever see '.fileB.metadata' file.
  * </div>
  *
  * @param workspace          the workspace in which the dependencies are expected to be uploaded. To.
  * @param dependsOnFiles     the filenames expected to be uploaded
  * @param timeoutInMillis    the time to wait for the dependencies to become available
  * @param awaitFlushedOutput if true, the file dependencies are expected to have been produced w/ a known file size, such as by a process which produced its output or a file which has been uploaded)
  */
case class UploadDependencies(workspace: WorkspaceId, dependsOnFiles: Set[String], timeoutInMillis: Long, awaitFlushedOutput: Boolean = true) {
  def addFile(file: String) = copy(dependsOnFiles = dependsOnFiles + file)

  def timeout = timeoutInMillis.millis

}
