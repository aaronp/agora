package agora.exec.workspace

import java.nio.file.Path

import agora.api.exchange.{Exchange, UpdateSubscription, UpdateSubscriptionAck}
import agora.api.json.{JPath, JsonDelta}
import agora.api.worker.SubscriptionKey
import agora.exec.WorkspacesKey
import agora.io.dao.Timestamp
import akka.http.scaladsl.util.FastFuture._
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.collection.GenTraversableOnce
import scala.concurrent.{ExecutionContext, Future}

/**
  * A workspace client which will append/remove workspaces from the given subscription based on the workspace
  * calls .
  *
  * This is meant to work along side [[agora.exec.client.RemoteRunner]] which may specify match criteria based
  * on a workspace.
  *
  * @param underlying an underlying workspace client to actually do the work we're proxying
  * @param exchange   the exchange where the subscriptions should be updated
  */
case class UpdatingWorkspaceClient(override val underlying: WorkspaceClient, exchange: Exchange)(implicit ec: ExecutionContext)
    extends WorkspaceClientDelegate {

  import UpdatingWorkspaceClient._

  private var allSubscriptions: Set[SubscriptionKey] = Set.empty

  private object Lock

  def addSubscriptions(newSubscriptions: GenTraversableOnce[SubscriptionKey]) = Lock.synchronized {
    allSubscriptions = allSubscriptions ++ newSubscriptions
  }

  def removeSubscription(uploadSubscription: SubscriptionKey) = Lock.synchronized {
    allSubscriptions = allSubscriptions - uploadSubscription
  }

  def subscriptions = allSubscriptions

  override def close(workspaceId: WorkspaceId, ifNotModifiedSince: Option[Timestamp], failPendingDependencies: Boolean) = {

    super.close(workspaceId, ifNotModifiedSince, failPendingDependencies).fast.flatMap { ack =>
      val futures = subscriptions.map { uploadSubscription =>
        removeWorkspaceFromSubscription(exchange, uploadSubscription, workspaceId)
      }

      val updatedSubscription = Future.sequence(futures)
      updatedSubscription.map {
        case _ => ack
      }
    }
  }

  override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]) = {
    val uploadFuture: Future[(Long, Path)] = super.upload(workspaceId, fileName, src)
    uploadFuture.fast.flatMap { ack: (Long, Path) =>
      val futures = subscriptions.map { uploadSubscription =>
        appendWorkspaceToSubscription(exchange, uploadSubscription, workspaceId)
      }
      val acks: Future[Set[UpdateSubscriptionAck]] = Future.sequence(futures)
      acks.fast.map {
        case _ => ack
      }
    }
  }
}

object UpdatingWorkspaceClient {

  import agora.api.Implicits._

  /**
    * appends a 'workspaces : [ ... , <workspaceId> ] ' to the subscription
    */
  def appendWorkspaceToSubscription(exchange: Exchange, subscriptionKey: SubscriptionKey, workspaceId: WorkspaceId) = {
    val newDetails = UpdateSubscription.append(subscriptionKey, WorkspacesKey, List(workspaceId))
    exchange.updateSubscriptionDetails(newDetails)
  }

  /**
    * removes the 'workspaces : [ ... , <workspaceId> ] ' from the subscription
    */
  def removeWorkspaceFromSubscription(exchange: Exchange, subscriptionKey: SubscriptionKey, workspaceId: WorkspaceId): Future[UpdateSubscriptionAck] = {
    val update =
      UpdateSubscription(subscriptionKey, condition = WorkspacesKey includes workspaceId, delta = JsonDelta.remove(JPath(WorkspacesKey) :+ workspaceId.inArray))
    exchange.updateSubscriptionDetails(update)
  }
}
