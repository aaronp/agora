package agora.api.exchange

import io.circe.generic.auto._
import agora.api.Implicits._
import agora.api.worker.SubscriptionKey
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{Matchers, WordSpec}

import scala.language.{postfixOps, reflectiveCalls}

trait ExchangeSpec extends WordSpec with Matchers with ScalaFutures with Eventually {

  import ExchangeSpec._

  /**
    * @return true when our exchange under test can support match observes (true for server-side, false for clients currently)
    */
  def supportsObserverNotifications = true

  def newExchange(observer: MatchObserver): Exchange

  getClass.getSimpleName.filter(_.isLetter).replaceAllLiterally("Test", "") should {
    "be able to cancel subscriptions" in {
      val obs = MatchObserver()
      val ex: Exchange = newExchange(obs)

      val subscription: WorkSubscriptionAck = ex.subscribe(WorkSubscription()).futureValue

      // check out queue
      val subscriptions = ex.queueState().futureValue.subscriptions.map(_.key)
      subscriptions should contain only (subscription.id)

      // call the method under test
      ex.cancelSubscriptions(subscription.id, "unknown").futureValue.canceledSubscriptions shouldBe Map(subscription.id -> true, "unknown" -> false)

      // check out queue
      val afterCancel = ex.queueState().futureValue.subscriptions
      afterCancel should be(empty)
    }
    "be able to cancel jobs" in {
      val obs = MatchObserver()
      val ex: Exchange = newExchange(obs)

      val input = DoubleMe(11).asJob.withAwaitMatch(false)
      input.jobId should be(empty)

      val jobId = ex.submit(input).futureValue.asInstanceOf[SubmitJobResponse].id

      // check out queue
      val queuedJobs = ex.queueState().futureValue.jobs.flatMap(_.jobId)
      queuedJobs should contain only (jobId)

      // call the method under test
      ex.cancelJobs(jobId, "unknownJob").futureValue.canceledJobs shouldBe Map(jobId -> true, "unknownJob" -> false)

      // check out queue
      val afterCancel = ex.queueState().futureValue.jobs
      afterCancel should be(empty)
    }

    if (supportsObserverNotifications) {
      "match jobs with work subscriptions" in {

        object obs extends MatchObserver
        var matches = List[Exchange.Match]()
        obs.alwaysWhen {
          case jobMatch => matches = jobMatch :: matches
        }
        val ex: Exchange = newExchange(obs)

        val jobId = ex.submit(DoubleMe(11).asJob.withAwaitMatch(false)).futureValue.asInstanceOf[SubmitJobResponse].id
        val jobPath = ("value" gt 7) and ("value" lt 17)

        val sub = WorkSubscription(jobMatcher = jobPath)

        val subscriptionId = ex.subscribe(sub).futureValue.id
        val consumedJob: RequestWorkAck = ex.take(subscriptionId, 1).futureValue

        matches.size shouldBe 1

        val List((_, requestWorkUpdate)) = consumedJob.updated.toList

        requestWorkUpdate.previousItemsPending shouldBe 0
        requestWorkUpdate.totalItemsPending shouldBe 0
      }
    }
  }
}

object ExchangeSpec {

  case class DoubleMe(value: Int)

}
