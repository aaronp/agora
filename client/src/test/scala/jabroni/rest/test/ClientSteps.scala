package jabroni.rest.test

import com.typesafe.config.ConfigFactory._
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import jabroni.api.{Order, OrderType, _}
import jabroni.rest.client.ClientConfig
import org.scalatest.Matchers

class ClientSteps extends ScalaDsl with EN with Matchers with TestData {

  var state = {
    ClientTestState()
  }

  When("""^I connect a client$""") {
    state = state.connect()
  }

  Given("""^the client configuration$""") { (config: String) =>
    val c = parseString(config).withFallback(ClientConfig.defaultConfig)
    state = state.copy(config = Option(ClientConfig(c)))
  }

  When("""^(.+) places a (.+) order for (.+)kg at £(.+)$""") { (user: String, orderType: String, quantity: String, priceGBP: String) =>
    val order = Order(user, OrderType.valueOf(orderType), asQuantity(quantity), asPrice(priceGBP))
    state = state.placeOrder(order)
  }

  When("""^(.+) cancels (.+) (.+) orders?$""") { (user: String, hisHerTheir: String, position: String) =>
    List(hisHerTheir) should contain oneOf("his", "her", "their", "all")
    val orders = position match {
      case "first" => state.firstOrderFor(user) :: Nil
      case "last" => state.lastOrderFor(user) :: Nil
      // She cancels 'all' 'her' orders
      case _ if hisHerTheir == "all" => state.ordersFor(user)
    }
    state = orders.foldLeft(state)(_ cancelOrder _)
  }

  Then("""^the cancel should return (.+)$""") { (trueFalse: Boolean) =>
    state.lastCancelStatus shouldBe trueFalse
  }

  Then("""^The order book should now be empty$""") { () =>
    val book = state.orderBook
    book.buyTotals shouldBe empty
    book.sellTotals shouldBe empty
  }

  When("""^(.+) cancels (.+) (.+) order for (.+)kg at £(.+)$""") { (user: String, hisHerTheir: String, orderType: String, quantity: String, priceGBP: String) =>
    List(hisHerTheir) should contain oneOf("his", "her", "their")
    val order = Order(user, OrderType.valueOf(orderType), asQuantity(quantity), asPrice(priceGBP))
    state = state.cancelOrder(order)
  }

  Then("""^The order book should be$""") { table: DataTable =>
    val expected = table.asOrderBook
    val actual = state.orderBook
    actual.buyTotals shouldBe expected.buyTotals
    actual.sellTotals shouldBe expected.sellTotals
  }

}
