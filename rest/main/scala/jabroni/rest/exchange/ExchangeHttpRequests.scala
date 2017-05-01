package jabroni.rest.exchange

import akka.http.scaladsl.model.HttpRequest
import jabroni.api.exchange.{SubscriptionRequest, RequestWork}

object ExchangeHttpRequests {

  def apply(r : SubscriptionRequest) : HttpRequest = {
    r match {
      case rw : RequestWork => ???
    }
  }

}
