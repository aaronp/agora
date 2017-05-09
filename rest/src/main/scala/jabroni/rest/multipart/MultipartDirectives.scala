package jabroni.rest.multipart

import akka.http.scaladsl.model.{HttpEntity, Multipart}
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.MarshallingDirectives.{as, entity}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future


trait MultipartDirectives {

  def multipartData: Directive1[MultipartPieces] =
    entity(as[Multipart.FormData]).flatMap { formData =>
      extractRequestContext.flatMap { ctx =>
        implicit val mat = ctx.materializer
        implicit val ec = ctx.executionContext

        val sources: Source[(MultipartInfo, Source[ByteString, Any]), Any] = formData.parts
          .map { part =>

            (MultipartInfo(part.name, part.filename, part.entity.contentType), part.entity.dataBytes)
          }

        val uploads = sources.runWith(Sink.seq[(MultipartInfo, Source[ByteString, Any])])

        onSuccess(uploads.map(seq => seq.toMap.ensuring(_.size == seq.size)))
      }
    }

}
