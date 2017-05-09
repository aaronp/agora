package jabroni.rest

import akka.stream.scaladsl.Source
import akka.util.ByteString

package object multipart {

  type MultipartPieces = Map[MultipartInfo, Source[ByteString, Any]]
}
