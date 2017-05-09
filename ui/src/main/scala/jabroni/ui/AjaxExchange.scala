package jabroni.ui

import io.circe
import io.circe.Decoder
import io.circe.generic.auto._
import jabroni.api.exchange._
import jabroni.api.json.JMatcher
import org.scalajs.dom.{XMLHttpRequest, window}
import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import language.reflectiveCalls
import language.implicitConversions

object AjaxExchange {

  def baseUrlFromWindow = s"${window.location.protocol}//${window.location.host}/rest/exchange"

  def apply(baseUrl: String = baseUrlFromWindow): AjaxExchange = new AjaxExchange(baseUrl)
}

class AjaxExchange(baseUrl: String)(implicit ec: ExecutionContext) extends Exchange {

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

  def jobs(subscriptionMatcher : JMatcher): Future[List[SubmitJob]] = {
    Ajax.get(s"$baseUrl/jobs").map(_.jsonAs[List[SubmitJob]])
  }

  def subscriptions(jobMatcher : JMatcher): Future[List[PendingSubscription]] = {
    //    Ajax.post(s"$baseUrl/subscriptions", request.asJson.noSpaces).map(_.jsonAs[ListSubscriptionsResponse])
    Ajax.get(s"$baseUrl/subscriptions").map(_.jsonAs[List[PendingSubscription]])
  }

  // weird .. this breaks scalajs
  //
  //  override def listJobs(request: QueuedJobs) : Future[QueuedJobsResponse] = ???
  //    Ajax.post(s"$baseUrl/jobs", request.asJson.noSpaces).map(_.jsonAs[QueuedJobsResponse])
  //  }
  //
  //  override def listSubscriptions(request: ListSubscriptions): Future[ListSubscriptionsResponse] = ???
  //    Ajax.post(s"$baseUrl/subscriptions", request.asJson.noSpaces).map(_.jsonAs[ListSubscriptionsResponse])
  //  }

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