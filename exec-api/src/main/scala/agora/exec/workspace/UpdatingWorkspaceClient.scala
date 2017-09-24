package agora.exec.workspace

import java.nio.file.Path

import agora.api.exchange.{Exchange, UpdateSubscriptionAck}
import agora.api.worker.{SubscriptionKey, WorkerDetails}
import agora.io.dao.Timestamp
import akka.http.scaladsl.util.FastFuture
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
class UpdatingWorkspaceClient(override val underlying: WorkspaceClient, exchange: Exchange, uploadSubscription: SubscriptionKey)(implicit ec : ExecutionContext) extends WorkspaceClientDelegate {

  override def close(workspaceId: WorkspaceId, ifNotModifiedSince: Option[Timestamp], failPendingDependencies: Boolean) = {

    super.close(workspaceId, ifNotModifiedSince, failPendingDependencies).fast.flatMap { ack =>
      //TODO - implement a 'compareAndSet' (or summat) so we can remove parts of the subscription json
      if (false) {
        val newDetails                                  = WorkerDetails.empty.append("workspaces", List(workspaceId))
        val updateFuture: Future[UpdateSubscriptionAck] = exchange.updateSubscriptionDetails(uploadSubscription, newDetails)
        updateFuture.map {
          case _ => ack
        }
      }

      FastFuture.successful(ack)
    }
  }

  override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]): Future[Path] = {
    val uploadFuture: Future[Path] = super.upload(workspaceId, fileName, src)

    uploadFuture.fast.flatMap { ack =>
      val newDetails = WorkerDetails.empty.append("workspaces", List(workspaceId))

      val updateFuture: Future[UpdateSubscriptionAck] = exchange.updateSubscriptionDetails(uploadSubscription, newDetails)
      updateFuture.map {
        case _ => ack
      }
    }
  }

}
