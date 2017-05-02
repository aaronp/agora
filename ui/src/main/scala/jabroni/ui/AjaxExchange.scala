package jabroni.ui

import io.circe
import io.circe.Decoder
import jabroni.api.exchange._
import org.scalajs.dom.{XMLHttpRequest, window}
import org.scalajs.dom.ext.Ajax
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.ExecutionContext
import language.reflectiveCalls

object AjaxExchange {

  def baseUrlFromWindow = s"${window.location.protocol}//${window.location.host}/rest/exchange"

  def apply(baseUrl: String = baseUrlFromWindow): AjaxExchange = new AjaxExchange(baseUrl)
}

class AjaxExchange(baseUrl: String)(implicit ec : ExecutionContext) extends Exchange {

  import io.circe.syntax._
  import io.circe.parser._

  implicit def richXMLHttpRequest(resp: XMLHttpRequest) = new {
    def jsonAs[T: Decoder]: T = {
      val either: Either[circe.Error, T] = parse(resp.responseText).right.flatMap(_.as[T])
      either match {
        case Left(err) => throw err
        case Right(ack) => ack
      }
    }
  }

  override def subscribe(request: WorkSubscription) = {
    val json = request.asJson.noSpaces
    Ajax.put(s"$baseUrl/subscribe", json).map(_.jsonAs[WorkSubscriptionAck])
  }

  override def take(request: RequestWork) = {
    val json = request.asJson.noSpaces
    Ajax.put(s"$baseUrl/take", json).map(_.jsonAs[RequestWorkAck])
  }

  override def submit(req: SubmitJob) = {
    val json = req.asJson.noSpaces
    Ajax.put(s"$baseUrl/submit", json).map(_.jsonAs[SubmitJobResponse])
  }
}