package jabroni.ui.js

import  jabroni.api.exchange._

import org.scalajs.dom
import org.scalajs.dom.window

class Services(val user: String, exchange : Exchange)

object Services { // extends JsonSupport {

  def apply(user: String = "anonymous"): Services = new Services(
    user,
    new JsExchange(s"${window.location.protocol}//${window.location.host}/rest")
  )

  class JsExchange(baseUrl: String) extends Exchange {
//    override def placeOrder(order: Order): Future[Boolean] = {
//      val json = order.toJsonString
//      val future: Future[XMLHttpRequest] = Ajax.put(s"$baseUrl/${order.`type`.toString.toLowerCase}", json)
//      future.map { resp =>
//        resp.status == 200 && resp.responseText.toBoolean
//      }
//    }
//
//    override def cancelOrder(order: Order): Future[Boolean] = {
//      val json = order.toJsonString
//      val future: Future[XMLHttpRequest] = Ajax.delete(s"$baseUrl/delete", json)
//      future.map { resp =>
//        require(resp.status == 200, s"Server replied w/ status ${resp.status}")
//        resp.responseText.toBoolean
//      }
//    }
//
//    override def orderBook: Future[OrderBook] = {
//      Ajax.get(s"$baseUrl/orders").map { resp =>
//        require(resp.status == 200, s"Server replied w/ status ${resp.status}")
//        resp.responseText.asOrderBook match {
//          case Left(err) => throw err
//          case Right(book) => book
//        }
//      }
//    }
  }

  def Alert(msg: String) = dom.window.alert(msg)
}
