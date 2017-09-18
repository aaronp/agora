package miniraft.state

import agora.BaseSpec
import agora.api.worker.HostLocation
import org.scalatest.concurrent.Eventually

import concurrent.duration._

class RaftConfigTest extends BaseSpec with Eventually {

  "RaftConfig.apply" should {
    "parse from command-line strings" in {
      val conf = RaftConfig("election.min=123ms")
      conf.election.min shouldBe 123.millis
    }
  }
  "RaftConfig.seedNodes" should {
    "be a list of host locations" in {
      RaftConfig.load().seedNodeLocations shouldBe List(HostLocation("0.0.0.0", 8080))
    }
  }
  "RaftConfig.election.timer" should {
    "invoke the given function on timeout" in {
      val c: RaftConfig = RaftConfig("election.min=10ms", "election.max=50ms")
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
    }
  }
}
