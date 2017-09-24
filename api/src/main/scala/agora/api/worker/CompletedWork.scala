package agora.api.worker

import io.circe.{Json, ParsingFailure}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

case class CompletedWork[T](work: List[(WorkerRedirectCoords, T)])(implicit executionContext: ExecutionContext) {

  def onlyWork = {
    val List(only) = work
    only
  }

  def onlyWorker: WorkerRedirectCoords = onlyWork._1

  def onlyResponse: T = onlyWork._2

}
