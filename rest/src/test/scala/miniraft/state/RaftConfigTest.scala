package miniraft.state

import agora.BaseRestSpec
import agora.api.config.HostLocation
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class RaftConfigTest extends BaseRestSpec with Eventually {

  "RaftConfig.apply" should {
    "parse from command-line strings" in {
      val conf = RaftConfig("election.min=123ms")
      try {
        conf.election.min shouldBe 123.millis
      } finally {
        conf.stop().futureValue
      }
    }
  }
  "RaftConfig.seedNodes" should {
    "be a list of host locations" in {
      val cfg = RaftConfig.load()
      try {
        cfg.seedNodeLocations shouldBe List(HostLocation("0.0.0.0", 8080))
      } finally {
        cfg.stop().futureValue
      }
    }
  }
  "RaftConfig.election.timer" should {
    "invoke the given function on timeout" in {
      val c: RaftConfig = RaftConfig("election.min=10ms", "election.max=50ms")
      try {
        c.election.min shouldBe 10.millis
        c.election.max shouldBe 50.millis
        var called = 0
        val eTimer = c.election.timer
        eTimer.initialise { t =>
          called = called + 1
        }
        eTimer.reset(None)
        eventually {
          called shouldBe 1
        }
      } finally {
        c.stop().futureValue
      }
    }
  }
}
