package jabroni.exec.log

import jabroni.exec.{ProcessError, RunProcess}
import jabroni.rest.BaseSpec

class ProcessLoggersTest extends BaseSpec {

  "ProcessLoggers" should {
    "propagate exceptions when given failure return codes" in {

      val proc = RunProcess("hi").copy(successExitCodes = Set(3), errorMarker = "Bang!")
      val logger = new ProcessLoggers("test", None, None, proc)
      logger.err("std err 1")
      logger.out("std out")
      logger.err("std err 2")
      logger.complete(1)

      val List("std out", "Bang!", json) = logger.iterator.toList
      val Right(error) = ProcessError.fromJsonString(json)

      error.exitCode shouldBe Option(1)
      error.process shouldBe proc
      error.stdErr shouldBe List("std err 1", "std err 2")
    }
  }
}
