package jabroni.ui

import jabroni.api.exchange._
import org.scalajs.dom
import org.scalajs.dom.window

class Services(val user: String, val exchange: Exchange) {
  def onError(err: Throwable) = Services.Alert(err.toString)
}

object Services {

  def baseUrlFromWindow = s"${window.location.protocol}//${window.location.host}"

  def apply(user: String = "anonymous"): Services = new Services(user, AjaxExchange())

  def Alert(msg: String) = dom.window.alert(msg)
}
