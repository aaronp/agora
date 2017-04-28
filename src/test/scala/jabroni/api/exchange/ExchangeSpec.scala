package jabroni.api.exchange

import io.circe.generic.auto._
import jabroni.api.Implicits._
import jabroni.api.client.SubmitJobResponse
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{Matchers, WordSpec}


trait ExchangeSpec extends WordSpec with Matchers with ScalaFutures with Eventually {

  import ExchangeSpec._

  def newExchange: Exchange

  "Exchange" should {
    "match jobs with work subscriptions" in {

      val ex = newExchange
      val jobId = ex.send(DoubleMe(34).asJob).futureValue.asInstanceOf[SubmitJobResponse].id

      val sub = WorkSubscription {
        case (job, remaining) =>
      }

      val subscriptionId = ex.pull(sub).futureValue.asInstanceOf[WorkSubscriptionAck].id

      val consumedJob = ex.take(subscriptionId, 1).futureValue
      consumedJob.totalItemsPending shouldBe 0


    }
  }
}

object ExchangeSpec {

  case class DoubleMe(value: Int)

}
