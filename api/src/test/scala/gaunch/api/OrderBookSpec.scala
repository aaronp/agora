package finance.api

import org.scalatest.{Matchers, WordSpec}

/**
  * Provides the specification for order books
  */
trait OrderBookSpec extends WordSpec with Matchers {

  def newOrderBook(buy: List[BuyValue], sell: List[SellValue]): OrderBook


  {

    /*
    SELL: 3.5 kg for £306 [user1]
- SELL: 1.2 kg for £310 [user2]
- SELL: 1.5 kg for £307 [user3]
- SELL: 2.0 kg for £306 [user4]
     */
    val book = newOrderBook(
      List(BuyValue(3.5, 306), BuyValue(1.2, 310), BuyValue(1.5, 307), BuyValue(2.0, 306)),
      List(SellValue(3.5, 306), SellValue(1.2, 310), SellValue(1.5, 307), SellValue(2.0, 306))
    )

    book.getClass.getSimpleName should {
      "order buy orders by increasing price" in {
        book.buyTotals shouldBe List(BuyValue(5.5, 306), BuyValue(1.5, 307), BuyValue(1.2, 310))
      }
      "order sell orders by decreasing price" in {
        book.sellTotals shouldBe List(SellValue(1.2, 310), SellValue(1.5, 307), SellValue(5.5, 306))
      }
    }
  }
}
