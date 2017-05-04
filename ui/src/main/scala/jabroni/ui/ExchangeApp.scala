package jabroni.ui

import org.scalajs.dom
import org.scalajs.dom.{document, html}

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportTopLevel

object ExchangeApp extends JSApp {

  def main(): Unit = {
    appendPar(document.body, """Jabroni""".stripMargin)
  }

  def appendPar(targetNode: dom.Node, text: String): Unit = {
    val parNode = document.createElement("p")
    val textNode = document.createTextNode(text)
    parNode.appendChild(textNode)
    targetNode.appendChild(parNode)
  }

  val svc: Services = Services()

  @JSExportTopLevel("onSubscribe")
  def onSubscribe(): Unit = Services.Alert("onSubscribe")

  @JSExportTopLevel("onTake")
  def onTake(n : Int): Unit = Services.Alert(s"onTake($n)")

  @JSExportTopLevel("onSubmit")
  def onSubmit(): Unit = {
    Services.Alert("onSubmit")
    //    val newBookFuture: Future[OrderBook] = svc.ledger.placeOrder(Order(svc.user, Sell, 12, 324)).flatMap { _ =>
    //      svc.ledger.orderBook
    //    }
    //    newBookFuture.onComplete(renderTable)
  }

  def reloadTable(targetNode: dom.Node) = {
    val kid = targetNode.firstChild
//    targetNode.replaceChild(kid, renderTable().render)
  }
//
//  def renderTable(book: OrderBook): JsDom.TypedTag[Table] = {
//    val header = tr(
//      td("Quantity"), td("Price")
//    )
//    //
//    //    val body = book.buyTotals.map {
//    //      case BuyValue(q,v) =>
//    //
//    //    }
//    //    val tbl = table(
//    //      header, body
//    //    )
//    //
//    //    tbl
//
//    ???
//  }
}
