package jabroni.api.exchange

import io.circe.generic.auto._
import jabroni.api.Implicits._
import jabroni.api.exchange.Exchange.OnMatch
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{Matchers, WordSpec}

import scala.language.reflectiveCalls
import scala.language.postfixOps

trait ExchangeSpec extends WordSpec with Matchers with ScalaFutures with Eventually {

  import ExchangeSpec._

  def newExchange[T](observer: OnMatch[T]): Exchange

  "Exchange" should {
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
      val consumedJob = ex.take(subscriptionId, 1).futureValue

      matches.size shouldBe 1

      consumedJob.totalItemsPending shouldBe 0
    }
  }
}

object ExchangeSpec {

  case class DoubleMe(value: Int)

}
