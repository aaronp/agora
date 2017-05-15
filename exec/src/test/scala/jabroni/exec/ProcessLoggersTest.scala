package jabroni.exec

import jabroni.exec.ProcessLoggers.StreamLogger
import jabroni.rest.BaseSpec

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import language.reflectiveCalls
import language.implicitConversions

class ProcessLoggersTest extends BaseSpec {

  implicit def testLogger(sl: StreamLogger) = new {
    // could potentially block the test indefinitely,so do this in (and out) of a future
    def asList(max: Int): List[String] = Future(sl.iterator.take(max).toList).futureValue
  }

  import ProcessLoggers._

  "ProcessLoggers.StreamLogger" should {
    "produce an iterator of output" in {
      val log: StreamLogger = StreamLogger(nonzeroException = true)
      log.out("first")
      log.out("second")
      log.asList(2) shouldBe List("first", "second")
    }
  }
  "LimitedLogger" should {
    "limit input up to a limit" in {
      val stream = StreamLogger()
      val log = LimitedLogger(limit = 3, stream)
      log.out("first out")
      log.err("first err")
      log.out("second out")
      log.err("second err")
      stream.complete(0)
      stream.asList(4) shouldBe List("first out", "first err", "second out") // limit reached after 3
    }
  }
}
