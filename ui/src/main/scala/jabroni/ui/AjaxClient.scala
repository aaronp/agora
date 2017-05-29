package jabroni.ui

import io.circe
import io.circe.Decoder
import io.circe.parser.parse
import org.scalajs.dom.XMLHttpRequest

import scala.concurrent.ExecutionContext

abstract class AjaxClient(val relativeUrl: String) {

  def baseUrl: String = Services.baseUrl + relativeUrl

  implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  import io.circe.parser._

  implicit def richXMLHttpRequest(resp: XMLHttpRequest) = new AjaxClient.RichXMLHttpRequest(resp)
}

object AjaxClient {

  implicit class RichXMLHttpRequest(val resp: XMLHttpRequest) extends AnyVal {
    def jsonAs[T: Decoder]: T = {
      val either: Either[circe.Error, T] = parse(resp.responseText).right.flatMap(_.as[T])
      either match {
        case Left(err) => throw err
        case Right(ack) => ack
      }
    }
  }

}