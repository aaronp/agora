package agora.exec.rest

import agora.exec.workspace.{WorkspaceClient, WorkspaceId}
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.Future

case class UploadRoutes(workspaces: WorkspaceClient) extends FailFastCirceSupport {

  def routes = uploadRoute ~ deleteWorkspace

  /**
    * Uploads some files to a workspace.
    *
    * We start with creating a subscription for 'topic : upload'.
    * When summat gets uploaded we add a subscription for 'workspace : xyz' based
    * on the upload subscription
    *
    * When a file is uploaded, a new subscription is added for the new files/workspace
    *
    * @return a Route used to update
    */
  def uploadRoute: Route = {
    (post & path("rest" / "exec" / "upload")) {
      extractMaterializer { implicit mat =>
        entity(as[Multipart.FormData]) { formData: Multipart.FormData =>
          parameter('workspace) { workspace =>
            val uploadFuture: Future[Boolean] = uploadToWorkspace(workspace, formData)
            complete(uploadFuture)
          }
        }
      }
    }
  }

  def deleteWorkspace: Route = {
    (delete & path("rest" / "exec" / "upload")) {
      parameter('workspace) { workspace =>
        complete(workspaces.close(workspace))
      }
    }
  }

  def uploadToWorkspace(workspace: WorkspaceId, formData: Multipart.FormData)(implicit mat: Materializer): Future[Boolean] = {
    import agora.rest.multipart.MultipartFormImplicits._
    import mat._

    val uploadSource: Source[Option[Future[Boolean]], Any] = formData.withMultipart {
      case (info, src) =>
        val fileName = info.fileName.getOrElse(info.fieldName)
        workspaces.upload(workspace, fileName, src)
    }

    val futureOptFuture: Future[Option[Future[Boolean]]] = uploadSource.runWith(Sink.head)
    futureOptFuture.flatMap { opt =>
      opt.getOrElse {
        Future.failed(new Exception("No multipart data sent"))
      }
    }
  }
}
