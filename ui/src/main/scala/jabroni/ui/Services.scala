package jabroni.ui

import jabroni.api.exchange._
import org.scalajs.dom

class Services(val user: String, val exchange: Exchange) {
  def onError(err: Throwable) = Services.Alert(err.toString)
}

object Services {


  def host = dom.document.location.host

  def protocol = dom.document.location.protocol

  def websocketProtocol: String = {
    dom.document.location.protocol match {
      case "https:" => "wss"
      case _ => "ws"
    }
  }

  def baseUrl = s"${protocol}//${host}"

  def baseWebsocketUri : String = {
    s"$websocketProtocol://$host"
  }

  def apply(user: String = "anonymous"): Services = new Services(user, AjaxExchange())

  def Alert(msg: String) = dom.window.alert(msg)
}
