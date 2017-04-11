package jabroni.api.js

import jabroni.api._
import jabroni.json.JsonSupport
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.XMLHttpRequest
import org.scalajs.dom.window

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Services(val user: String, val ledger: Ledger)

object Services extends JsonSupport {

  def apply(user: String = "anonymous"): Services = new Services(
    user,
    new JsLedger(s"${window.location.protocol}//${window.location.host}/rest")
  )

  class JsLedger(baseUrl: String) extends Ledger {
    override def placeOrder(order: Order): Future[Boolean] = {
      val json = order.toJsonString
      val future: Future[XMLHttpRequest] = Ajax.put(s"$baseUrl/${order.`type`.toString.toLowerCase}", json)
      future.map { resp =>
        resp.status == 200 && resp.responseText.toBoolean
      }
    }

    override def cancelOrder(order: Order): Future[Boolean] = {
      val json = order.toJsonString
      val future: Future[XMLHttpRequest] = Ajax.delete(s"$baseUrl/delete", json)
      future.map { resp =>
        require(resp.status == 200, s"Server replied w/ status ${resp.status}")
        resp.responseText.toBoolean
      }
    }

    override def orderBook: Future[OrderBook] = {
      Ajax.get(s"$baseUrl/orders").map { resp =>
        require(resp.status == 200, s"Server replied w/ status ${resp.status}")
        resp.responseText.asOrderBook match {
          case Left(err) => throw err
          case Right(book) => book
        }
      }
    }
  }

  //
  //  private def put(relativePath: String) = mkXhr("PUT", relativePath)
  //
  //  private def get(relativePath: String) = mkXhr("GET", relativePath)
  //
  //  private def mkXhr(method: String, relativePath: String) = {
  //    val req = new XMLHttpRequest()
  //    val path = s"$baseUrl/rest/$relativePath"
  //    req.open(method, path, true)
  //    req.setRequestHeader("Content-Type", "application/json")
  //    req.setRequestHeader("Accept", "application/json")
  //    req
  //  }
  //
  //
  //  def newOrder(user: String, typ: String, qty: Int, price: Int) = {
  //    s"""{"user":"${user}","type":"${typ.toUpperCase}","quantity":${qty},"price":$price}"""
  //    Order(user, OrderType.valueOf(typ), qty, price).toJsonString
  //  }
  //
  //  def reloadOrders() = {
  //    val xhr = get("orders")
  //    xhr.onload = (e: dom.Event) => {
  //      val summary: js.Dynamic = JSON.parse(xhr.responseText)
  //
  //      // TODO - before we go any further with these JSON strings, we should really
  //      // make the api/json work with our ui project
  //
  //      //      Alert()
  //      appendPar(document.body, xhr.responseText)
  //    }
  //    xhr.send()
  //  }

  def Alert(msg: String) = dom.window.alert(msg)
}
