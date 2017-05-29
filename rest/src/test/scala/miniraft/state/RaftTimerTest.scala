package miniraft.state

import jabroni.rest.BaseSpec

class RaftTimerTest extends BaseSpec {

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
      val timer = RaftTimer(10.millis, 100.millis, "test") { timer =>
        require(cancelOnTimeout.isEmpty)
        cancelOnTimeout = Option(timer.cancel())
      }
      // the timer should initially not be running
      timer.cancel() shouldBe false

      // reset immediately
      timer.reset(Option(0.millis))

      // canceling a timed-out timer should be false
      cancelOnTimeout shouldBe Option(false)
    }
    "cancel and reschedule timers" in {

      var calls = 0
      val timer = RaftTimer(10.millis, 100.millis, "test") { timer =>
        calls = calls + 1
        if (calls < 10) {
          timer.reset(Option(0.millis))
        }
      }
      // the timer should initially not be running
      timer.reset(Option(0.millis))

      calls shouldBe 10
    }
  }

}
