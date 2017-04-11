package jabroni.json

import jabroni.api.{Order, OrderBook, OrderType, OrderValue}
import io.circe.Decoder.Result
import language.implicitConversions

trait JsonSupport {

  import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
  // import io.circe._
  // import io.circe.generic.auto._
  // import io.circe.parser._
  // import io.circe.syntax._

  implicit object OrderTypeJson extends Encoder[OrderType] with Decoder[OrderType] {
    override def apply(a: OrderType): Json = Json.fromString(a.toString.toUpperCase)

    override def apply(c: HCursor): Result[OrderType] = {
      c.as[String].right.map(OrderType.valueOf)
    }
  }


  implicit def richString(jsonString: String) = new {
    def asOrder: Either[Error, Order] = decode[Order](jsonString)

    def asOrderBook: Either[Error, OrderBook] = decode[OrderBook.BookAggregation](jsonString)
  }

  implicit def richOrder(order: Order) = new {
    def toJsonString = toJson.noSpaces

    def toJson: Json = order.asJson
  }

  implicit def richOrderBook(book: OrderBook) = new {
    def toJsonString = toJson.noSpaces

    def toJson: Json = OrderBook.BookAggregation(book.buyTotals, book.sellTotals).asJson
  }

  implicit val orderBookDecoder: Decoder[OrderBook] = implicitly[Decoder[OrderBook]]

}
