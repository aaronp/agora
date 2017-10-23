package agora.rest.ws

import akka.http.javadsl.model.ws.{TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.model.HttpRequest

object ServerWebSocket {

  def upgrade(req: HttpRequest) = {
    req.header[UpgradeToWebSocket].map { upgrade =>
      }
  }

  def lift(f: TextMessage => Unit) = {}
}
