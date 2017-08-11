package agora.exec.model

import agora.rest.BaseSpec

class RunProcessTest extends BaseSpec {
  "RunProcess.resolveEnv" should {
    "replace dollar-side delimited keys in the command strings" in {
      val runProcess = RunProcess(List("$KEY/$KEYS/$KEY/x/$KEY"), Map("KEY" -> "foo"))
      runProcess.resolveEnv.command shouldBe List("foo/$KEYS/foo/x/foo")
    }
  }

}
