package jabroni.exec

import jabroni.rest.BaseSpec

import scala.concurrent.duration._

class ExecConfigTest extends BaseSpec {

  "ExecConfig.defaultConfig" should {
    "contain uploadTimeout" in {

      val ec = ExecConfig(Array.empty)
      ec.uploadTimeout shouldBe 10.seconds
    }
  }
}
