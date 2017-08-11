package agora.ui

import agora.api.exchange._
import agora.ui.worker.AjaxExecute
import org.scalajs.dom
import org.scalajs.dom.raw.Location

class Services(val user: String, val exchange: Exchange) {
  def onError(err: Throwable) = Services.Alert(err.toString)

  def execute = AjaxExecute
}

object Services {

  def loc: Location = dom.document.location

  def host = loc.host

  def protocol = loc.protocol

  def websocketProtocol: String = {
    loc.protocol match {
      case "https:" => "wss"
      case _        => "ws"
    }
  }

  def baseUrl = s"${protocol}//${host}"

  def baseWebsocketUri: String = s"$websocketProtocol://${loc.host}"

  def apply(user: String = "anonymous"): Services = new Services(user, AjaxExchange())

  def Alert(msg: String) = dom.window.alert(msg)
}
