package jabroni.rest

import akka.stream.scaladsl.Source
import akka.util.ByteString

/**
  * This package contains some general ultilities for working with multi-part messages
  */
package object multipart {

  type MultipartPieces = Map[MultipartInfo, Source[ByteString, Any]]
}
