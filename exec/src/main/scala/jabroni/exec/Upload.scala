package jabroni.exec

import akka.http.scaladsl.model.{ContentType, ContentTypes}
import akka.stream.scaladsl.Source
import akka.util.ByteString

case class Upload(name: String,
                  size: Long,
                  source: Source[ByteString, Any],
                  contentType: ContentType = ContentTypes.`text/plain(UTF-8)`)
