package jabroni.rest

import jabroni.rest.worker.{WorkerConfig, Worker}
import org.scalatest.{Matchers, WordSpec}

class WorkerTest extends WordSpec with Matchers {

  "WorkerMain.configForArgs" should {
    "produce a worker config from user args" in {
      val serverConf = Worker.configForArgs(Array("port=1122", "exchange.port=567"))
      val wc: WorkerConfig = WorkerConfig(serverConf)
      wc.location.port shouldBe 1122
      wc.exchangeClientConfig.location.port shouldBe 567

    }
  }
}