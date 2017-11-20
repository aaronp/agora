package agora.exec.rest

import java.nio.file.Path

import agora.api.exchange.Exchange
import agora.api.worker.{SubscriptionKey, WorkerDetails}
import agora.exec.workspace.{WorkspaceClient, WorkspaceId}
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.swagger.annotations._

import scala.concurrent.Future

@Api(value = "Workspace", produces = "application/json")
@javax.ws.rs.Path("/")
class UploadRoutes(initialWorkspaces: WorkspaceClient) extends FailFastCirceSupport with StrictLogging {

  @volatile private var workspaces = initialWorkspaces

  def routes = uploadRoute ~ deleteWorkspace

  /**
    * Uploads some files to a workspace.
    *
    * When summat gets uploaded we add a subscription for 'workspace : xyz' based
    * on the upload subscription
    *
    * When a file is uploaded, a new subscription is added for the new files/workspace
    *
    * @return a Route used to update
    */
  @javax.ws.rs.Path("/rest/exec/upload")
  @ApiOperation(value = "Uploads the multipart file to the specified workspace",
                httpMethod = "POST",
                produces = "application/json",
                consumes = "multipart/form-data")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", required = true, paramType = "form", dataType = "file", value = "The upload contents"),
      new ApiImplicitParam(name = "workspace", required = true, paramType = "query", value = "The workspace name")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Returns true on success")
    ))
  def uploadRoute: Route = {
    (post & path("rest" / "exec" / "upload")) {
      extractMaterializer { implicit mat =>
        entity(as[Multipart.FormData]) { formData: Multipart.FormData =>
          parameter('workspace) { workspace =>
            val uploadFuture = uploadToWorkspace(workspace, formData)
            complete(uploadFuture)
          }
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/exec/upload")
  @ApiOperation(value = "Deletes the specified workspace and all files in it", httpMethod = "DELETE", produces = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "workspace", required = true, paramType = "query", value = "The workspace name to delete")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Returns true if the workspace was deleted as a result of this call")
    ))
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

    val uploadSource = formData.withMultipart {
      case (info, src) =>
        val fileName = info.fileName.getOrElse(info.fieldName)

        logger.info(s"Uploading ${workspace}/${fileName}")

        val future = workspaces.upload(workspace, fileName, src)

        import agora.io.implicits._
        future.map(_._2.exists())
    }

    val futureOptFuture: Future[Option[Future[Boolean]]] = uploadSource.runWith(Sink.head)
    futureOptFuture.flatMap { opt =>
      opt.getOrElse {
        Future.failed(new Exception("No multipart data sent"))
      }
    }
  }
}

object UploadRoutes {
  def apply(initialWorkspaces: WorkspaceClient) = new UploadRoutes(initialWorkspaces)
}
