package jabroni


package object api {

  type Quantity = BigDecimal
  type Price = BigDecimal
  type UserId = String

  def asPrice(str : String) = BigDecimal(str)
  def asQuantity(str : String) = BigDecimal(str)
}
