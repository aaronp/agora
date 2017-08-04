package agora.exec.run

import agora.exec.model.Upload
import agora.exec.workspace.WorkspaceId
import agora.io.Sources
import agora.rest.client.RestClient
import agora.rest.multipart.MultipartBuilder
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  * Something with can upload data via a [[RestClient]]
  */
trait UploadClient extends FailFastCirceSupport {

  /** @return the rest client used to upload
    */
  def client: RestClient

  /**
    * Convenience method to upload a file
    *
    * @param workspaceId the workspace (e.g. directory) where this should be uploaded
    * @param file        the data to upload
    * @param timeout     the timeout used to marshal the request
    * @return a future containing a success flag
    */
  final def upload(workspaceId: WorkspaceId, file: Upload)(implicit mat: Materializer, timeout: FiniteDuration): Future[Boolean] = {
    import mat.executionContext
    Sources.sizeOf(file.source).flatMap { len =>
      upload(workspaceId, file.name, len, file.source)
    }
  }

  /**
    * upload some data to a 'workspace' (e.g. consistent session/directory)
    *
    * @param workspaceId the workspace to which the data will be uploaded
    * @param fileName    the file name
    * @param len         the length of the source data to upload
    * @param src         the data source of the data to upload
    * @param contentType the content type of the data
    * @param timeout     the timeout used to marshal the data into a request
    * @return a future of a success flag
    */
  def upload(workspaceId: WorkspaceId, fileName: String, len: Long, src: Source[ByteString, Any], contentType: ContentType = ContentTypes.`text/plain(UTF-8)`)(
      implicit timeout: FiniteDuration): Future[Boolean] = {
    val c = client
    import c.{executionContext, materializer}
    for {
      request  <- UploadClient.asRequest(workspaceId, fileName, len, src, contentType)
      response <- c.send(request)
      ok       <- Unmarshal(response).to[Boolean]
    } yield {
      ok
    }
  }

  def deleteWorkspace(workspaceId: WorkspaceId): Future[Boolean] = {
    val c = client
    import c.{executionContext, materializer}
    c.send(UploadClient.asDeleteRequest(workspaceId)).flatMap { resp =>
      Unmarshal(resp).to[Boolean]
    }
  }
}

object UploadClient extends RequestBuilding {

  case class Instance(override val client: RestClient) extends UploadClient

  def apply(client: RestClient) = Instance(client)

  def asDeleteRequest(workspaceId: WorkspaceId)(implicit mat: Materializer) = {
    val uri = Uri("/rest/exec/upload").withQuery(Query(Map("workspace" -> workspaceId)))
    Delete(uri)
  }

  def asRequest(workspaceId: WorkspaceId, fileName: String, len: Long, src: Source[ByteString, Any], contentType: ContentType)(implicit mat: Materializer,
                                                                                                                               timeout: FiniteDuration) = {

    import mat._

    val b = MultipartBuilder(contentType).fromStrictSource(fileName, len, src, fileName = fileName)
    b.formData.map { fd =>
      val uri = Uri("/rest/exec/upload").withQuery(Query(Map("workspace" -> workspaceId)))
      Post(uri, fd)
    }

  }
}
