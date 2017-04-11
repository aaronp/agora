package jabroni.json

import scala.language.reflectiveCalls

import jabroni.api._
import org.scalatest.{Matchers, WordSpec}

class JsonSupportTest extends WordSpec with Matchers with JsonSupport {

  val order = Order("somebody", Buy, 1, 2)
  val expectedJson = """{"user":"somebody","type":"BUY","quantity":1,"price":2}"""
  "Order.toJson" should {
    "convert an order to json" in {
      order.toJsonString shouldBe expectedJson
    }
  }
  "Order.fromJson" should {
    "convert an order from json" in {
      val Right(backAgain) = expectedJson.asOrder
      backAgain shouldBe order
    }
  }
  "OrderBook.fromJson" should {
    "convert an order from json" in {
      val orderBookJson = """{"buyTotals":[{"quantity":3,"price":306}],"sellTotals":[{"quantity":1,"price":123}]}"""
      val Right(book) = orderBookJson.asOrderBook
      book.buyTotals shouldBe List(BuyValue   (3, 306))
      book.sellTotals shouldBe List(SellValue(1, 123))
    }
  }
}
