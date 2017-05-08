package jabroni.rest.exchange

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, SinkQueue, SinkQueueWithCancel, Source}
import akka.util.ByteString
import io.circe.{Json, ParsingFailure}
import jabroni.api.worker.WorkerRedirectCoords

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

case class CompletedWork(work: List[(WorkerRedirectCoords, HttpResponse)])(implicit mat: Materializer) {

  import mat.executionContext

  def onlyWork = {
    val List(only) = work
    only
  }

  def onlyWorker: WorkerRedirectCoords = onlyWork._1

  def onlyResponse: HttpResponse = onlyWork._2

  def sourceResponse: Source[ByteString, Any] = onlyResponse.entity.dataBytes

  def iterateResponse(timeout: FiniteDuration = 10.seconds)(implicit ec: ExecutionContext) = {
    import CompletedWork._
    val q: SinkQueueWithCancel[ByteString] = sourceResponse.runWith(Sink.queue())
    q.iterator(timeout)
  }

  def jsonResponse: Future[Either[ParsingFailure, Json]] = {
    val bytes: Future[ByteString] = sourceResponse.runWith(Sink.reduce(_ ++ _))
    val content: Future[String] = bytes.map(_.decodeString("UTF-8"))
    content.map { json =>
      io.circe.parser.parse(json)
    }
  }


}

object CompletedWork {

  implicit class RichQueue[T](val queue: SinkQueue[T]) extends AnyVal {
    def iterator(timeout: FiniteDuration)(implicit ec: ExecutionContext): Iterator[T] = {
      val future: Future[Option[T]] = queue.pull()
      val next: Option[T] = Await.result(future, timeout)

      val iter = next.iterator
      if (iter.hasNext) {
        iter ++ iterator(timeout)
      } else {
        iter
      }
    }
  }

}