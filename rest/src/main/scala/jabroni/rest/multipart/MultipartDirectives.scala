package jabroni.rest.multipart

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.MarshallingDirectives.{as, entity}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future


trait MultipartDirectives {

  def multipartData: Directive1[MultipartPieces] =
    entity(as[Multipart.FormData]).flatMap { (formData: Multipart.FormData) =>
      extractRequestContext.flatMap { ctx =>
        val uploadMap = parseFormData(formData)(ctx.materializer)
        onSuccess(uploadMap)
      }
    }

  def parseFormData(formData: Multipart.FormData)(implicit mat: Materializer): Future[MultipartPieces] = {
    import mat._
    val sources: Source[(MultipartInfo, Source[ByteString, Any]), Any] = formData.parts
      .map { part =>
        (MultipartInfo(part.name, part.filename, part.entity.contentType), part.entity.dataBytes)
      }

    val uploads = sources.runWith(Sink.seq[(MultipartInfo, Source[ByteString, Any])])
    uploads.map(seq => seq.toMap.ensuring(_.size == seq.size))
  }
}
