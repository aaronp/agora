package jabroni.rest

import jabroni.rest.worker.WorkerConfig
import org.scalatest.{Matchers, WordSpec}

class WorkerTest extends WordSpec with Matchers {

  "Worker.configForArgs" should {
    "produce a worker config from user args" in {
      val wc: WorkerConfig = WorkerConfig(Array("details.path=foo", "port=1122", "exchange.port=567"))
      wc.location.port shouldBe 1122
      wc.exchangeClientConfig.location.port shouldBe 567
      wc.subscription.details.location.port shouldBe 1122
      wc.subscription.details.path shouldBe Option("foo")

    }
  }
}
