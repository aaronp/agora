package jabroni.exec.model


import java.nio.file.Path

import akka.http.scaladsl.model.{ContentType, ContentTypes}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

case class Upload(name: String,
                  size: Long,
                  source: Source[ByteString, Any],
                  contentType: ContentType = ContentTypes.`text/plain(UTF-8)`)

object Upload {
  def apply(path: Path) = {
    import jabroni.domain.io.implicits._
    new Upload(path.toFile.getName, path.size, FileIO.fromPath(path))
  }
}