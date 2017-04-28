package jabroni.rest.exchange

import jabroni.api.client.{SubmitJob, SubmitJobResponse}
import jabroni.api.exchange.Exchange
import jabroni.rest.BaseSpec

import scala.language.reflectiveCalls
import jabroni.api.Implicits._

/**
  * In this test, we could assert the response marshalling,
  * but it's worth as well having tests which cover explicit json as strings, just in case we accidentally break
  * that form by e.g. renaming a parameter. that would potentially break clients running against different
  * versions of our service, or dynamic languages (e.g. javascript )
  */
class ExchangeRoutesTest extends BaseSpec {

  def routes() = {
    ExchangeRoutes(Exchange()).routes
  }

  "PUT /rest/exchange/submit" should {
    "submit jobs" in {
      ExchangeHttp(123.asJob()) ~> routes() ~> check {
        val resp = responseAs[SubmitJobResponse]
        resp.id should not be (null)
      }
    }
  }
}
