package jabroni.ui

import io.circe.generic.auto._
import jabroni.api.exchange._
import jabroni.api.json.JMatcher
import org.scalajs.dom.ext.Ajax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.{implicitConversions, reflectiveCalls}

object AjaxExchange {
  def apply(): AjaxExchange = new AjaxExchange()
}

class AjaxExchange() extends AjaxClient("/rest/exchange") with Exchange {

  import io.circe.syntax._

  def jobs(subscriptionMatcher: JMatcher): Future[List[SubmitJob]] = {
    Ajax.get(s"$baseUrl/jobs").map(_.jsonAs[List[SubmitJob]])
  }

  def subscriptions(jobMatcher: JMatcher): Future[List[PendingSubscription]] = {
    Ajax.get(s"$baseUrl/subscriptions").map(_.jsonAs[List[PendingSubscription]])
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

  /**
    * Queue the state of the exchange
    *
    * @param request
    * @return the current queue state
    */
  override def queueState(request: QueuedState): Future[QueuedStateResponse] = {
    val json = request.asJson.noSpaces
    Ajax.post(s"$baseUrl/queue", json).map(_.jsonAs[QueuedStateResponse])
  }

  override def cancelJobs(request: CancelJobs): Future[CancelJobsResponse] = {
    val json = request.asJson.noSpaces
    Ajax.delete(s"$baseUrl/jobs", json).map(_.jsonAs[CancelJobsResponse])
  }

  override def cancelSubscriptions(request: CancelSubscriptions): Future[CancelSubscriptionsResponse] = {
    val json = request.asJson.noSpaces
    Ajax.delete(s"$baseUrl/subscriptions", json).map(_.jsonAs[CancelSubscriptionsResponse])
  }
}