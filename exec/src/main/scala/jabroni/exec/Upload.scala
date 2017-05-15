package jabroni.exec

import akka.stream.scaladsl.Source
import akka.util.ByteString

case class Upload(name : String, size :Long, source : Source[ByteString, Any])
