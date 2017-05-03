package jabroni.rest.worker

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.circe.DecodingFailure
import jabroni.api.worker.{DispatchWork, WorkerDetails}
import jabroni.rest.client.RestClient

case class WorkerClient(rest: RestClient, path: String) {
  def dispatch(req: DispatchWork) = rest.send(WorkerHttp(path, req))
}

object WorkerClient {
  def apply(detail: WorkerDetails)(implicit sys : ActorSystem, mat: Materializer): Either[DecodingFailure, WorkerClient] = {
    val rest = RestClient(detail.location)
    detail.valueOf[String]("path").right.map { path =>
      new WorkerClient(rest, path)
    }
  }
}
