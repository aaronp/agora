package jabroni.rest

import jabroni.rest.worker.WorkerConfig
import org.scalatest.{Matchers, WordSpec}

class WorkerTest extends WordSpec with Matchers {

  "Worker.configForArgs" should {
    "produce a worker config from user args" in {
      val wc: WorkerConfig = WorkerConfig("details.path=foo", "port=1122", "exchange.port=567")
      wc.location.port shouldBe 1122
      wc.exchangeConfig.location.port shouldBe 567

      wc.subscription.details shouldBe wc.workerDetails

      wc.subscription.details.location.port shouldBe 1122
      wc.subscription.details.path shouldBe Option("foo")

    }
  }
}
