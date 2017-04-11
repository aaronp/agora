package jabroni.api

sealed trait OrderType

object OrderType {
  def valueOf(str: String) = str.trim.toLowerCase match {
    case "buy" => Buy
    case "sell" => Sell
    case other => sys.error(s"Invalid order type '${other}'")
  }
}

case object Buy extends OrderType

case object Sell extends OrderType

/**
  * Can represent either a single order details or an aggregate value in an order book.
  *
  * This is a tricky one to model -- or at least has several considerations.
  *
  * One one hand you could just have the single
  * {{{
  *   OrderValue(quantity, price)
  * }}}
  *
  * case class, but we have a requirement that they are treated differently (i.e. how they are ordered).
  *
  * Buy representing the value total as different types, it should hopefully help us avoid costly problems
  * (e.g. historically where a metric unit has been confused with a USCS one!).
  *
  * This DOES mean that we end up with more boilerplate ... but arguable we WANT that boilerplate as we
  * shouldn't be able to seemlessly combine Buy and Sell values.
  *
  * BuyValue and SellValue due share this common base trait, however, to allow us to operate commonly in places
  * where they only need a quantity and value, or to be able to e.g. match on all known instances of this sealed trait.
  *
  * The disadvantages of the latter are that we can't supply other 'OrderValue's externally to this codebase.
  *
  */
sealed trait OrderValue {
  type T <: OrderValue

  def quantity: Quantity

  def price: Price

  def total: Price = quantity * price
}

case class SellValue(override val quantity: Quantity, override val price: Price) extends OrderValue {
  type T = SellValue
}

object SellValue {
  def fromOrder(order: Order): SellValue = SellValue(order.quantity, order.price)

  implicit object SellOrder extends Ordering[SellValue] {
    val priceOrder = Ordering[Price].reverse

    override def compare(x: SellValue, y: SellValue): Int = priceOrder.compare(x.price, y.price)
  }

}

case class BuyValue(override val quantity: Quantity, override val price: Price) extends OrderValue {
  type T = BuyValue
}

object BuyValue {

  def fromOrder(order: Order): BuyValue = BuyValue(order.quantity, order.price)

  implicit object BuyOrder extends Ordering[BuyValue] {
    val priceOrder = Ordering[Price]

    override def compare(x: BuyValue, y: BuyValue): Int = priceOrder.compare(x.price, y.price)
  }

}


case class Order(user: UserId, `type`: OrderType, quantity: Quantity, price: Price)
