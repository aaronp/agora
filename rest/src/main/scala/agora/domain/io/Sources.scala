package agora.domain.io

import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

object Sources {

  implicit class RichSource[O, M](val src: Source[O, M]) extends AnyVal {
    def onComplete[Out, Mat](onComplete: => Unit): Source[O, M] = runOnComplete(src)(onComplete)
  }

  def sizeOf(src: Source[ByteString, Any])(implicit mat: akka.stream.Materializer): Future[Long] = {
    src.map(_.size.toLong).runReduce(_ + _)
  }

  def asBytes[T](src: Source[Any, T], newLine: String = "\n"): Source[ByteString, T] = {
    src.map { x =>
      ByteString(s"$x$newLine")
    }
  }

  def asText(src: Source[ByteString, Any])(implicit materializer: akka.stream.Materializer): Future[String] = {
    import materializer._
    src.runReduce(_ ++ _).map(_.utf8String)
  }

  /**
    * Invoke the curried function after the last element is produced from the given source when materialized
    * @param src the source on which to alert
    * @param onComplete the callback function
    * @tparam Out
    * @tparam Mat
    * @return a new notifying source
    */
  def runOnComplete[Out, Mat](src: Source[Out, Mat])(onComplete: => Unit): Source[Out, Mat] = {
    val notifyingFlow = OnComplete.onUpstreamComplete {
      case _ => onComplete
    }

    src.via(notifyingFlow)
  }
}
