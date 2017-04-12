package jabroni.rest.test

import java.io.Closeable

import jabroni.api.{Order, OrderBook}
import jabroni.rest.client.{ClientConfig, JabroniClient}
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

case class ClientTestState(config: Option[ClientConfig] = None,
                           clientOpt: Option[JabroniClient] = None,
                           orders: List[Order] = Nil,
                           cancels: List[(Order, Boolean)] = Nil)
  extends Matchers
    with ScalaFutures
    with AutoCloseable {

  def lastCancelStatus = cancels.head._2

  def cancelOrder(order: Order) = {
    val result = client.cancelOrder(order).futureValue
    copy(cancels = (order -> result) :: cancels)
  }

  def ordersFor(user: String) = orders.filter(_.user == user)

  // NOTE: orders are in most-recent order!
  def firstOrderFor(user: String) = ordersFor(user).last

  // NOTE: orders are in most-recent order!
  def lastOrderFor(user: String) = ordersFor(user).head

  def placeOrder(order: Order) = {
    val result = client.placeOrder(order).futureValue
    result shouldBe true
    copy(orders = order :: orders)
  }

  def orderBook: OrderBook = client.orderBook.futureValue

  def connect() = {
    close()
    ClientTestState(clientOpt = Option(JabroniClient(config.get)))
  }

  def client = clientOpt.get

  override def close() = {
    clientOpt.foreach {
      case c: Closeable => c.close()
      case _ =>
    }
  }

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(150, Millis)))
}
