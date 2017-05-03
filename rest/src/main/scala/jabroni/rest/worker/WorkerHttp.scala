package jabroni.rest.worker

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.Json
import jabroni.api.worker.DispatchWork

object WorkerHttp extends RequestBuilding {

  import io.circe.syntax._
  import io.circe.generic.auto._

  def apply(path: String, request: DispatchWork): HttpRequest = post(path, request.asJson)

  private def post(path: String, json: Json): HttpRequest = {
    val e = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
    Post(path).withEntity(e)
  }

  object execute {
    def failed = {
      HttpResponse(status = StatusCodes.BadRequest)
    }

    def response(lines: Iterator[String]) = {
      val src = Source.fromIterator(() => lines).map(line => ByteString.fromString(line))
      val body = HttpEntity(ContentTypes.`application/octet-stream`, src)
      HttpResponse(entity = body)
    }
  }

}
