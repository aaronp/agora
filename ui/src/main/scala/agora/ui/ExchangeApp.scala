package agora.ui

import org.scalajs.dom
import org.scalajs.dom.{document, html}

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportTopLevel

object ExchangeApp extends JSApp {

  def main(): Unit = {
    //appendPar(document.body, """Jabroni""".stripMargin)
  }

  val svc: Services = Services()

  @JSExportTopLevel("onSubscribe")
  def onSubscribe(): Unit = Services.Alert("onSubscribe")

  @JSExportTopLevel("onTake")
  def onTake(n: Int): Unit = Services.Alert(s"onTake($n)")

  @JSExportTopLevel("onSubmit")
  def onSubmit(): Unit = {
    Services.Alert("onSubmit")
    //    val newBookFuture: Future[OrderBook] = svc.ledger.placeOrder(Order(svc.user, Sell, 12, 324)).flatMap { _ =>
    //      svc.ledger.orderBook
    //    }
    //    newBookFuture.onComplete(renderTable)
  }

  def reloadTable(targetNode: dom.Node) = {}
}
