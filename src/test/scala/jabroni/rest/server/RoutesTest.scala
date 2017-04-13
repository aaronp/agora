package jabroni.rest.server

import scala.language.reflectiveCalls

/**
  * In this test, we could assert the response marshalling,
  * but it's worth as well having tests which cover explicit json as strings, just in case we accidentally break
  * that form by e.g. renaming a parameter. that would potentially break clients running against different
  * versions of our service, or dynamic languages (e.g. javascript )
  */
class RoutesTest extends BaseSpec {

  //
  //  "GET /rest/orders" should {
  //    "return no orders for empty order books" in {
  //      Get("/rest/orders") ~> routes() ~> check {
  //        responseAs[String] shouldBe "{\"buyTotals\":[],\"sellTotals\":[]}"
  //      }
  //    }
  //    "return buy and sell orders" in {
  //      val ledger = Ledger() buy (1) at (2) sell (3) at (4)
  //      Get("/rest/orders") ~> routes(ledger) ~> check {
  //        responseAs[String] shouldBe "{\"buyTotals\":[{\"quantity\":1,\"price\":2}],\"sellTotals\":[{\"quantity\":3,\"price\":4}]}"
  //      }
  //    }
  //  }
  //
  //  "PUT /rest/buy" should {
  //    "place BUY orders" in {
  //      val ledger = Ledger()
  //
  //      val body = HttpEntity("""{"user":"hello","type":"BUY","quantity":1,"price":2}""").
  //        withContentType(ContentTypes.`application/json`)
  //
  //      HttpRequest(PUT, Uri("/rest/buy"), entity = body) ~> routes(ledger) ~> check {
  //
  //        ledger.orderBook.futureValue.buyTotals should contain only (BuyValue(1, 2))
  //        responseAs[String] shouldBe "true"
  //        response.status.intValue shouldBe 200
  //      }
  //    }
  //  }
  //  "PUT /rest/sell" should {
  //    "place SELL orders" in {
  //      val ledger = Ledger()
  //
  //      val body = HttpEntity("""{"user":"hello","type":"SELL","quantity":1,"price":2}""").
  //        withContentType(ContentTypes.`application/json`)
  //
  //      HttpRequest(PUT, Uri("/rest/sell"), entity = body) ~> routes(ledger) ~> check {
  //        ledger.orderBook.futureValue.sellTotals should contain only (SellValue(1, 2))
  //        responseAs[String] shouldBe "true"
  //        response.status.intValue shouldBe 200
  //      }
  //    }
  //  }
  //  "DELETE /rest/cancel" should {
  //    "cancel orders" in {
  //      val ledger = Ledger().buy(100).at(1)
  //
  //      val body = HttpEntity("""{"user":"somebody","type":"SELL","quantity":1,"price":2}""").
  //        withContentType(ContentTypes.`application/json`)
  //
  //      HttpRequest(PUT, Uri("/rest/sell"), entity = body) ~> routes(ledger) ~> check {
  //        ledger.orderBook.futureValue.sellTotals should contain only (SellValue(1, 2))
  //        responseAs[String] shouldBe "true"
  //        response.status.intValue shouldBe 200
  //      }
  //    }
  //  }
  //
  //  "GET /ui/index.html" in {
  //    Get("/ui/index.html") ~> routes() ~> check {
  //      response.status.intValue() shouldBe 200
  //      responseAs[String] should startWith("<!DOCTYPE html>")
  //    }
  //  }
  //  "GET /ui/js/jabroni-ui-fastopt.js" ignore {
  //    Get("/ui/js/jabroni-ui-fastopt.js") ~> routes() ~> check {
  //      response.status.intValue() shouldBe 200
  //      responseAs[String] should include("The top-level Scala.js environment")
  //    }
  //  }
}
