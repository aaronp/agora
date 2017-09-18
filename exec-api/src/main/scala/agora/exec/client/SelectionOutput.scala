package agora.exec.client

import agora.api.SubscriptionKey
import agora.api.worker.HostLocation
import agora.exec.model.RunProcessResult
import agora.exec.workspace.WorkspaceId
import agora.rest.client.RestClient
import akka.http.scaladsl.model.{ContentType, ContentTypes}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * A data structure containing the result of an execution via the [[RemoteRunner]].
  *
  * The use-case driving this is to have a consistent, 'sticky-session' type workflow with
  * a single worker which could still be either load-balanced via the exchange or targeted
  * directly for subsequent requests.
  *
  * The workflow used to produce a 'SelectionOutput' is this:
  *
  * 1) a job is submitted to the [[agora.api.exchange.Exchange]]
  * 2) the job gets matched with some worker taking subscriptions.
  * 3) we want to remember that worker, as well as proceed with executing the job from step 1
  *
  * The SelectionOutput is the 'remembering' of that worker -- allowing us to submit using the same subscription (runner)
  *
  * @param subscription the subscription id of the remote runner
  * @param runner       the remote runner which will match a worker based on the subscription
  * @param uploader     an uploader which can be used to upload data to the worker represented by the runner
  * @param result       the result of the execution
  */
case class SelectionOutput(subscription: SubscriptionKey, location: HostLocation, runner: RemoteRunner, uploader: UploadClient, result: Future[RunProcessResult])
    extends UploadClient {

  /** @return the rest client used to upload
    */
  override def client: RestClient = uploader.client

  override def upload(workspaceId: WorkspaceId, fileName: String, len: Long, src: Source[ByteString, Any], contentType: ContentType = ContentTypes.`text/plain(UTF-8)`)(
      implicit timeout: FiniteDuration): Future[Boolean] = {
    uploader.upload(workspaceId, fileName, len, src, contentType)
  }

  override def deleteWorkspace(workspaceId: WorkspaceId) = {
    uploader.deleteWorkspace(workspaceId)
  }
}
