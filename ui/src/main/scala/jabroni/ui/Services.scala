package jabroni.ui

import jabroni.api.exchange._
import org.scalajs.dom

class Services(val user: String, val exchange: Exchange, val observer : QueueObserver) {
  def onError(err: Throwable) = Services.Alert(err.toString)
}

object Services {

  def apply(user: String = "anonymous"): Services = new Services(user, AjaxExchange(), null)

  def Alert(msg: String) = dom.window.alert(msg)
}
