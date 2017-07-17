package agora.exec.session

import agora.api.JobId
import agora.exec.model.{RunProcess, Upload}
import agora.exec.run.ProcessRunner
import agora.rest.exchange.ExchangeClient

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Represents something which can upload files and execute commands using those files separately.
  *
  * Where a [[ProcessRunner]] encapsulates its uploads and commands together in one request, this
  * interface separates the uploading of files from the execution of commands which work on those files.
  *
  */
trait SessionRunner {

  def startSession(id: SessionId): Future[JobId]

  /**
    *
    * @param id the session id with which to run the job
    * @param cmd the thing to run
    * @param uploadDependencies a set of uploads which are expected to exist before this job runs
    * @return
    */
  def run(id: SessionId, cmd: RunProcess, uploadDependencies: Set[String]): ProcessRunner.ProcessOutput

  def upload(id: SessionId, uploads: List[Upload]): Future[Boolean]

  final def upload(id: SessionId, firstFile: Upload, theRest: Upload*): Future[Boolean] = upload(id, firstFile :: theRest.toList)

  def closeSession(id: SessionId)
}

object SessionRunner {

  def apply(exchange: ExchangeClient, frameLength: Int, allowTruncation: Boolean)(implicit timeout: FiniteDuration) =
    new RemoteSessionRunner(exchange, frameLength, allowTruncation)

}
