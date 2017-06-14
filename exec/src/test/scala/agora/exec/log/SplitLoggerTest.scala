package agora.exec.log

import agora.rest.BaseSpec

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits._

class SplitLoggerTest extends BaseSpec {

  // could potentially block the test indefinitely,so do this in (and out) of a future
  implicit def testLogger(sl: StreamLogger) = new {
    def asList(max: Int): List[String] = Future(sl.iterator.take(max).toList).futureValue
  }

  "SplitLogger.iterator" should {

    "complete its stream logger" in {
      val stdErrLog = StreamLogger()

      val stdOutLog = StreamLogger.forProcess {
        case n if n != 0 => stdErrLog.iterator.toStream
      }

      val split = SplitLogger(JustStdOut(stdOutLog), JustStdErr(stdErrLog))
      split.streamLoggers.size shouldBe 2

      split.out("this is std out text")
      split.err("this is std err text")
      split.complete(1)

      stdOutLog.iterator.toList shouldBe List("this is std out text", "this is std err text")
    }
    "complete its stream logger if stdErr is under a limited logger and comes first" in {
      val stdErrLog = StreamLogger()

      val stdOutLog = StreamLogger.forProcess {
        case n if n != 0 => stdErrLog.iterator.toStream
      }

      val split = SplitLogger(LimitLogger(10, JustStdErr(stdErrLog)), JustStdOut(stdOutLog))
      split.streamLoggers.size shouldBe 2

      split.out("this is std out text")
      split.err("this is std err text")
      split.complete(1)

      stdOutLog.iterator.toList shouldBe List("this is std out text", "this is std err text")
    }
  }
}
