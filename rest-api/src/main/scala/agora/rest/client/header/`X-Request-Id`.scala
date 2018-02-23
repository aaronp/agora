package agora.rest.client.header

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{ModeledCustomHeader, ModeledCustomHeaderCompanion}

import scala.util.Try

// https://stackoverflow.com/questions/41593401/can-akka-httpheader-store-an-x-request-id-or-x-correlation-id-field
final class `X-Request-Id`(id: String) extends ModeledCustomHeader[`X-Request-Id`] {
  override def renderInRequests = true

  override def renderInResponses = true

  override val companion = `X-Request-Id`

  override def value: String = id
}

object `X-Request-Id` extends ModeledCustomHeaderCompanion[`X-Request-Id`] {
  override val name = "X-Request-Id"

  override def parse(value: String) = Try(new `X-Request-Id`(value))

  def requestIdOfRequest(req: HttpRequest): Option[String] = {
    for {
      `X-Request-Id`(id) <- req.header[`X-Request-Id`]
    } yield id
  }
}
