package finance.api

class OrderBookTest extends OrderBookSpec {
  override def newOrderBook(buy: List[BuyValue], sell: List[SellValue]): OrderBook = {

    OrderBook(buy, sell)

  }
}
