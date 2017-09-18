package agora.rest.client

import java.time.LocalDateTime

import agora.BaseSpec
import akka.http.scaladsl.client.RequestBuilding
import agora.rest.client.RetryStrategy.CountingStrategy

import scala.concurrent.duration.{FiniteDuration, _}

class RetryStrategyTest extends BaseSpec with RequestBuilding {

  import Crashes._

  implicit def richCrash(c: Crash) = new {
    def plus(d: FiniteDuration): Crash = c.copy(time = c.time.plusNanos(d.toNanos))

    def minus(d: FiniteDuration): Crash = c.copy(time = c.time.minusNanos(d.toNanos))
  }

  "CountingStrategy.filter" should {
    val strategy: CountingStrategy = new CountingStrategy(2, 1.day)
    val jan1                       = LocalDateTime.of(2017, 1, 1, 0, 0, 0)
    class Bang extends Exception("bang")
    def jan1Crash = Crash(new Bang, jan1)

    "remove exceptions which occurred outside the window" in {
      strategy.filter(Crashes(jan1Crash :: Nil), jan1.plusHours(25)).size shouldBe 0
      strategy.filter(Crashes(jan1Crash :: jan1Crash.plus(1.day) :: jan1Crash.minus(1.day) :: Nil), jan1.plusHours(25)).size shouldBe 1
    }
    "leave empty crashes alone" in {
      strategy.filter(Crashes(Nil), jan1).size shouldBe 0
    }
    "not remove exceptions within the window" in {
      strategy.filter(Crashes(jan1Crash :: Nil), jan1).size shouldBe 1
    }
    "throw an exception when the threshold is breached" in {
      val err = intercept[Bang] {
        strategy.filter(Crashes(List(jan1Crash, jan1Crash, jan1Crash)), jan1)
      }
      err.getSuppressed.length shouldBe 2
    }
  }
}
