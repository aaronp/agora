package agora.rest.exchange

import agora.api.exchange.{Compose, _}
import agora.rest.CommonRequestBuilding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest}
import io.circe.Json

/**
  * Contains the functions for converting our messages into HttpRequests.
  *
  * This should go hand-in-glove with ExchangeRoutes
  */
object ExchangeHttp extends CommonRequestBuilding {

  import io.circe.syntax._
  import io.circe.generic.auto._

  def apply(request: CancelJobs): HttpRequest = {
    delete("jobs", request.asJson)
  }

  def apply(compose: Compose): HttpRequest = post("compose", compose.asJson)

  def apply(request: CancelSubscriptions): HttpRequest = {
    import io.circe.generic.auto._
    delete("subscriptions", request.asJson)
  }

  def apply(request: QueueState): HttpRequest = post("queue", request.asJson)

  def apply(request: SubmitJob): HttpRequest = put("submit", request.asJson)

  def apply(request: WorkSubscription): HttpRequest = put("subscribe", request.asJson)

  def apply(request: UpdateWorkSubscription): HttpRequest = post("update", request.asJson)

  def apply(request: RequestWork): HttpRequest = post("take", request.asJson)

  private def put(path: String, json: Json): HttpRequest = {
    val e = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
    Put(s"/rest/exchange/$path").withEntity(e).withCommonHeaders
  }

  private def post(path: String, json: Json): HttpRequest = {
    val e = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
    Post(s"/rest/exchange/$path").withEntity(e).withCommonHeaders
  }

  private def delete(path: String, json: Json): HttpRequest = {
    val e = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
    Delete(s"/rest/exchange/$path").withEntity(e).withCommonHeaders
  }
}
