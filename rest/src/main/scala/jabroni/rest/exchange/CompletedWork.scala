package jabroni.rest.exchange

import javafx.scene.paint.Material

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import io.circe.{Json, ParsingFailure}
import jabroni.api.worker.WorkerRedirectCoords

import scala.concurrent.Future

case class CompletedWork(work: List[(WorkerRedirectCoords, HttpResponse)])(implicit mat: Materializer) {

  import mat.executionContext

  def onlyWork = {
    val List(only) = work
    only
  }

  def onlyWorker: WorkerRedirectCoords = onlyWork._1

  def onlyResponse: HttpResponse = onlyWork._2

  def jsonResponse: Future[Either[ParsingFailure, Json]] = {
    val bytes: Future[ByteString] = onlyResponse.entity.dataBytes.runWith(Sink.reduce(_ ++ _))
    val content: Future[String] = bytes.map(_.decodeString("UTF-8"))
    content.map { json =>
      io.circe.parser.parse(json)
    }
  }


}
