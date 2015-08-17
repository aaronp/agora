package finance.rest.test

import cucumber.api.DataTable
import finance.api.{OrderBook, OrderType, _}

import language.implicitConversions

object TestData {

  class RichTable(val table: DataTable) extends AnyVal {

    import scala.collection.JavaConverters._

    def toMap: List[Map[String, String]] = {
      table.asMaps(classOf[String], classOf[String]).asScala.toList.map(_.asScala.toMap)
    }

    def asScala: List[List[String]] = table.raw.asScala.toList.map(_.asScala.toList)

    def asOrderBook: OrderBook = {
      val (buy, sell) = asScala.map(asOrder).foldLeft(List.empty[BuyValue] -> List.empty[SellValue]) {
        case ((buyList, sellList), (Buy, q, p)) => (BuyValue(q, p) :: buyList, sellList)
        case ((buyList, sellList), (Sell, q, p)) => (buyList, SellValue(q, p) :: sellList)
      }
      OrderBook(buy, sell)
    }
  }

  private val QtyR = "(.*)kg".r
  private val PriceR = "£(.*)".r

  def asOrder(row: List[String]) = {
    val List(ordType, QtyR(qty), PriceR(price)) = row
    (OrderType.valueOf(ordType), asQuantity(qty), asPrice(price))
  }

}

trait TestData {

  import TestData._

  implicit def asRichTable(dataTable: DataTable): RichTable = new RichTable(dataTable)

}
