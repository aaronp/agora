package miniraft.state

import agora.BaseSpec
import agora.rest.HasMaterializer
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.util.Try

class RaftTimerTest extends BaseSpec with HasMaterializer with Eventually {

  import concurrent.duration._

  "RaftTimer.timeouts" should {
    "produce timeouts in a given range" in {
      val timeouts = RaftTimer.timeouts(100.millis, 400.millis).take(1000).toList
      timeouts.find(_ < 100.millis) shouldBe None
      timeouts.find(_ > 400.millis) shouldBe None

      // we should have random timeouts
      val set = timeouts.map(_.toMillis).toSet

      // TODO - assert the distribution a bit better... this is going to be a flaky test
      // as it's based on random numbers
      set.size should be > 50
    }
  }
  "RaftTimer" should {
    "cancel a timed out timer should be false" in {

      var cancelOnTimeout: Option[Boolean] = None
      @volatile var callbackInvoked        = false
      val timer                            = InitialisableTimer("test", 10.millis, 100.millis)
      timer.initialise { x =>
        require(cancelOnTimeout.isEmpty)
        callbackInvoked = true
        x.cancel().onSuccess {
          case cancelled =>
            cancelOnTimeout = Option(cancelled)
        }
      }.futureValue shouldBe true

      // the timer should initially not be running
      timer.cancel().futureValue shouldBe false

      // reset immediately
      timer.reset(Option(0.millis))

      // canceling a timed-out timer should be false
      eventually {
        callbackInvoked shouldBe true
      }
      eventually {
        cancelOnTimeout shouldBe Option(false)
      }
    }
    "cancel and reschedule timers" in {

      var calls = 0
      val timer = InitialisableTimer("test", 10.millis, 100.millis)

      timer.initialise { x =>
        calls = calls + 1
        if (calls < 10) {
          x.reset(Option(0.millis))
        }
      }

      // the timer should initially not be running
      timer.reset(Option(0.millis))

      eventually {
        calls shouldBe 10
      }
    }
  }

}
