package jabroni.exec.log

import jabroni.exec.model.{ProcessError, RunProcess}
import jabroni.rest.BaseSpec

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class ProcessLoggersTest extends BaseSpec {

  "ProcessLoggers" should {
    "return an empty iterator if completed before any output is sent" in {
      val logger = IterableLogger(RunProcess("hi"))
      logger.complete(0)
      logger.iterator.hasNext shouldBe false
    }
    "return an empty iterator if we try and consume the iterator before completing" in {
      val logger = IterableLogger(RunProcess("hi"))
      val iterFut = Future(logger.iterator.hasNext)
      logger.complete(0)
      iterFut.futureValue shouldBe false
    }
    "propagate exceptions when given failure return codes" in {

      val proc = RunProcess("hi").copy(successExitCodes = Set(3), errorMarker = "Bang!")
      val logger = IterableLogger(proc)
      logger.err("std err 1")
      logger.out("std out")
      logger.err("std err 2")
      logger.complete(1)

      val json = logger.iterator.toList match {
        case "std out" :: "Bang!" :: json => json.mkString("\n")
        case _ => ???
      }
      val Right(error) = ProcessError.fromJsonString(json)

      error.exitCode shouldBe Option(1)
      error.process shouldBe proc
      error.stdErr shouldBe List("std err 1", "std err 2")
    }
  }
}
