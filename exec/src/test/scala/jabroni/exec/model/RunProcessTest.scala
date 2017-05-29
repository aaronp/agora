package jabroni.exec.model

import jabroni.rest.BaseSpec

class RunProcessTest extends BaseSpec {
  "RunProcess json" should {
    "parse valid json" in {
      val path = "uploads/2ce6f1cf-ea2c-4fb3-bb08-cd5f50e19a57/runProcess.json".asPath
      val json = path.text
      println(json)

    }
  }

}
