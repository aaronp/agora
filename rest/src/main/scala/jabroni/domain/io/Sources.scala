package jabroni.domain.io

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

object Sources {

  def sizeOf(src: Source[ByteString, Any])(implicit mat: akka.stream.Materializer): Future[Long] = {
    src.map(_.size.toLong).runReduce(_ + _)
  }

  def asBytes[T](src: Source[Any, T], newLine: String = "\n"): Source[ByteString, T] = {
    src.map { x => ByteString(s"$x$newLine") }
  }

}
