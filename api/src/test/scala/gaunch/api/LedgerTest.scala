package jabroni.api

import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

class LedgerTest extends OrderBookSpec with ScalaFutures {

  "Ledger.remove" should {
    "remote the first item found in a list" in {
      val (true, list) = Ledger.remove(9, List(1, 9, 2))
      list shouldBe List(1, 2)
    }
    "return false if the item was not removed" in {
      val (false, list) = Ledger.remove(9, List(1, 2))
      list shouldBe List(1, 2)
    }
  }
  "Ledger.cancelOrder" should {
    "cancel an order" in {
      val buy = Order("foo", Buy, 1, 1)
      val sell = Order("foo", Sell, 1, 1)
      val ledger = newLedger(Nil, Nil)
      Future.sequence(ledger.placeOrder(buy) :: ledger.placeOrder(sell) :: Nil).futureValue
      val initialBook = ledger.orderBook.futureValue
      initialBook.buyTotals.size shouldBe 1
      initialBook.sellTotals.size shouldBe 1

      ledger.cancelOrder(buy.copy(quantity = buy.quantity + 1)).futureValue shouldBe false
      ledger.cancelOrder(buy).futureValue shouldBe true
      ledger.orderBook.futureValue.buyTotals shouldBe empty
      ledger.cancelOrder(buy).futureValue shouldBe false

      ledger.cancelOrder(sell.copy(user = "somebody else")).futureValue shouldBe false
      ledger.cancelOrder(sell).futureValue shouldBe true
      ledger.cancelOrder(sell).futureValue shouldBe false
      ledger.orderBook.futureValue.sellTotals shouldBe empty
    }
  }

  def newLedger(buy: List[BuyValue], sell: List[SellValue]): Ledger = {
    val typesWithValue = buy.iterator.map(Buy -> _) ++ sell.iterator.map(Sell -> _)
    val orders = typesWithValue.map {
      case (orderType: OrderType, value) => Order("", orderType, value.quantity, value.price)
    }
    orders.foldLeft(Ledger()) {
      case (ledger, order) => ledger.placeOrder(order)
        ledger
    }
  }

  override def newOrderBook(buy: List[BuyValue], sell: List[SellValue]): OrderBook = {
    val x = newLedger(buy, sell)
    val f = x.orderBook
    import concurrent.duration._
    val b = Await.result(f, 1.second)
    b
  }

}
