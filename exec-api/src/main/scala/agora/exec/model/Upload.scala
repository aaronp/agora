package agora.exec.model

import java.nio.file.Path

import agora.io.implicits._
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

case class Upload(name: String, source: Source[ByteString, Any], size: Option[Long] = None)

object Upload {
  def apply(path: Path) = {
    new Upload(path.toFile.getName, FileIO.fromPath(path), Option(path.size))
  }

  def forText(name: String, text: String) = {
    val bytes: ByteString = ByteString(text)
    forBytes(name, bytes)
  }

  def forBytes(name: String, bytes: ByteString) = new Upload(name, Source.single(bytes), Option(bytes.size))
}
