package jabroni.rest.multipart

import akka.http.scaladsl.model.Multipart
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future

object MultipartDirectives extends StrictLogging {

  implicit class RichFormData(val formData: Multipart.FormData) extends AnyVal {
    def mapMultipart[T](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), T])(implicit mat: Materializer): Future[List[T]] = {
      import mat._
      withMultipart(f).runWith(Sink.seq).map(_.flatten.toList)
    }

    def mapFirstMultipart[T](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), T])(implicit mat: Materializer): Future[T] = {
      import mat._
      withMultipart(f).collect {
        case Some(tea) => tea
      }.runWith(Sink.headOption).map { opt =>
        opt.getOrElse(sys.error(s"No multiparts were found which matched"))
      }
    }

    def withMultipart[T](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), T])(implicit mat: Materializer): Source[Option[T], Any] = {
      formData.parts.map { part =>
        val info = MultipartInfo(part.name, part.filename, part.entity.contentType)
        val src = part.entity.dataBytes
        if (f.isDefinedAt(info, src)) {
          Option(f(info, src))
        } else {
          src.runWith(Sink.ignore)
          None
        }
      }
    }
  }

}