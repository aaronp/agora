package agora.exec.log

import agora.rest.BaseSpec

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import language.implicitConversions
import language.reflectiveCalls

class LimitLoggerTest extends BaseSpec {

  // could potentially block the test indefinitely,so do this in (and out) of a future
  implicit def testLogger(sl: StreamLogger) = new {
    def asList(max: Int): List[String] = Future(sl.iterator.take(max).toList).futureValue
  }

  "LimitLogger" should {
    "limit input up to a limit" in {
      val stream = StreamLogger()
      val log    = LimitLogger(limit = 3, stream)
      log.out("first out")
      log.err("first err")
      log.out("second out")
      log.err("second err")
      stream.complete(0)
      stream.asList(4) shouldBe List("first out", "first err", "second out") // limit reached after 3
    }
  }
}
