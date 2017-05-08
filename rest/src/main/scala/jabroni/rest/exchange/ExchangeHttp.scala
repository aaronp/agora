package jabroni.rest.exchange

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest}
import io.circe.Json
import jabroni.api.exchange._

/**
  * Contains the functions for converting our messages into HttpRequests.
  *
  * This should go hand-in-glove with ExchangeRoutes
  */
object ExchangeHttp extends RequestBuilding {

  import io.circe.syntax._

  def apply(request: QueuedJobs): HttpRequest = post("jobs", request.asJson)

  def apply(request: ListSubscriptions): HttpRequest = post("subscriptions", request.asJson)

  def apply(request: SubmitJob): HttpRequest = put("submit", request.asJson)

  def apply(request: WorkSubscription): HttpRequest = put("subscribe", request.asJson)

  def apply(request: RequestWork): HttpRequest = post("take", request.asJson)

  private def put(path: String, json: Json): HttpRequest = {
    val e = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
    Put(s"/rest/exchange/$path").withEntity(e)
  }

  private def post(path: String, json: Json): HttpRequest = {
    val e = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
    Post(s"/rest/exchange/$path").withEntity(e)
  }
}
