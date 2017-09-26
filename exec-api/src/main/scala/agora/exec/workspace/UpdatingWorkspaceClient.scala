package agora.exec.workspace

import java.nio.file.Path

import agora.api.exchange.{Exchange, UpdateSubscription, UpdateSubscriptionAck}
import agora.api.json.{JPath, JsonDelta}
import agora.api.worker.SubscriptionKey
import agora.io.dao.Timestamp
import akka.http.scaladsl.util.FastFuture._
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

/**
  * A workspace client which will append/remove workspaces from the given subscription
  *
  * @param underlying
  * @param exchange
  * @param uploadSubscription
  */
class UpdatingWorkspaceClient(override val underlying: WorkspaceClient,
                              exchange: Exchange,
                              uploadSubscription: SubscriptionKey)(implicit ec: ExecutionContext)
    extends WorkspaceClientDelegate {

  import UpdatingWorkspaceClient._

  override def close(workspaceId: WorkspaceId,
                     ifNotModifiedSince: Option[Timestamp],
                     failPendingDependencies: Boolean) = {

    super.close(workspaceId, ifNotModifiedSince, failPendingDependencies).fast.flatMap { ack =>
      val updatedSubscription = removeWorkspaceFromSubscription(exchange, uploadSubscription, workspaceId)
      updatedSubscription.map {
        case _ => ack
      }
    }
  }

  override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]): Future[Path] = {
    val uploadFuture: Future[Path] = super.upload(workspaceId, fileName, src)
    uploadFuture.fast.flatMap { ack =>
      appendWorkspaceToSubscription(exchange, uploadSubscription, workspaceId).fast.map {
        case _ => ack
      }
    }
  }

}

object UpdatingWorkspaceClient {

  import agora.api.Implicits._

  /**
    *
    * @param exchange
    * @param subscriptionKey
    * @param workspaceId
    * @return
    */
  def appendWorkspaceToSubscription(exchange: Exchange, subscriptionKey: SubscriptionKey, workspaceId: WorkspaceId) = {
    val newDetails = UpdateSubscription.append(subscriptionKey, "workspaces", List(workspaceId))
    exchange.updateSubscriptionDetails(newDetails)
  }

  def removeWorkspaceFromSubscription(exchange: Exchange,
                                      subscriptionKey: SubscriptionKey,
                                      workspaceId: WorkspaceId): Future[UpdateSubscriptionAck] = {
    val update = UpdateSubscription(subscriptionKey,
                                    condition = "workspaces" includes workspaceId,
                                    delta = JsonDelta.remove(JPath("workspaces") :+ workspaceId.inArray))
    exchange.updateSubscriptionDetails(update)
  }
}
