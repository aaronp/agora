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
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

case class UploadClient(client: RestClient)(implicit timeout: FiniteDuration) extends FailFastCirceSupport {

  final def upload(workspaceId: WorkspaceId, file: Upload): Future[Boolean] = {
    upload(workspaceId, file.name, file.source)
  }

  def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any], contentType: ContentType = ContentTypes.`text/plain(UTF-8)`): Future[Boolean] = {
    import client.{executionContext, materializer}
    for {
      request  <- UploadClient.asRequest(workspaceId, fileName, src, contentType)
      response <- client.send(request)
      ok       <- Unmarshal(response).to[Boolean]
    } yield ok
  }

  def deleteWorkspace(workspaceId: WorkspaceId): Future[Boolean] = {
    import client.{executionContext, materializer}
    client.send(UploadClient.asDeleteRequest(workspaceId)).flatMap { resp =>
      Unmarshal(resp).to[Boolean]
    }
  }
}

object UploadClient extends RequestBuilding {

  def asDeleteRequest(workspaceId: WorkspaceId)(implicit mat: Materializer, timeout: FiniteDuration) = {
    val uri = Uri("/rest/exec/upload").withQuery(Query(Map("workspace" -> workspaceId)))
    Delete(uri)
  }

  def asRequest(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any], contentType: ContentType)(implicit mat: Materializer, timeout: FiniteDuration) = {

    import mat._
    Sources.sizeOf(src).flatMap { len =>
      val b = MultipartBuilder(contentType).fromStrictSource(fileName, len, src, fileName = fileName)
      b.formData.map { fd =>
        val uri = Uri("/rest/exec/upload").withQuery(Query(Map("workspace" -> workspaceId)))
        Post(uri, fd)
      }
    }

  }
}
