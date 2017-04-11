package jabroni.api.js

import jabroni.api.{BuyValue, Order, OrderBook, Sell}
import org.scalajs.dom
import org.scalajs.dom.html.Table
import org.scalajs.dom.raw.XMLHttpRequest
import org.scalajs.dom.{document, window}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.{JSApp, JSON}
import scalatags.JsDom
import scalatags.JsDom.implicits._
import scalatags.JsDom.tags._

object FinanceApp extends JSApp {

  def main(): Unit = {
    appendPar(document.body, """Live Orders!""".stripMargin)
  }

  def appendPar(targetNode: dom.Node, text: String): Unit = {
    val parNode = document.createElement("p")
    val textNode = document.createTextNode(text)
    parNode.appendChild(textNode)
    targetNode.appendChild(parNode)
  }

  val svc = Services()

  @JSExportTopLevel("onSell")
  def onSell(): Unit = {
    val newBookFuture: Future[OrderBook] = svc.ledger.placeOrder(Order(svc.user, Sell, 12, 324)).flatMap { _ =>
      svc.ledger.orderBook
    }
    newBookFuture.onComplete(renderTable)
  }

  @JSExportTopLevel("onBuy")
  def onBuy(): Unit = {
    val xhr = put("buy")
    xhr.onload = (e: dom.Event) => {
      val ok = xhr.responseText.toBoolean
      if (ok) {
        reloadOrders()
      }
    }
    xhr.send(newOrder("user", "Buy", 5, 6))
  }


  def reloadTable(targetNode: dom.Node) = {
    val kid = targetNode.firstChild
    targetNode.replaceChild(kid, renderTable().render)

  }

  def renderTable(book: OrderBook): JsDom.TypedTag[Table] = {
    val header = tr(
      td("Quantity"), td("Price")
    )

    val body = book.buyTotals.map {
      case BuyValue(q,v) =>

    }
    val tbl = table(
      header, body
    )

    tbl
  }
}
