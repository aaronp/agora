package jabroni.exec.log

import jabroni.rest.BaseSpec
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import language.implicitConversions
import language.reflectiveCalls

class StreamLoggerTest extends BaseSpec with Matchers {

  // could potentially block the test indefinitely,so do this in (and out) of a future
  implicit def testLogger(sl: StreamLogger) = new {
    def asList(max: Int): List[String] = Future(sl.iterator.take(max).toList).futureValue
  }

  "StreamLogger.iterator" should {

    "produce an iterator of output" in {
      val log: StreamLogger = StreamLogger()
      log.out("first")
      log.out("second")
      log.asList(2) shouldBe List("first", "second")
    }

    "stream output" in {
      val log = StreamLogger()
      // we should be able to call 'iterator' here, before any output is given
      val streamIter = log.iterator
      Future(
        Iterator.continually("some text").zipWithIndex.foreach {
          case (s, i) =>
            log.out(s"$i: $s")
        }
      )
      val oneHundred = streamIter.take(100)
      oneHundred.size shouldBe 100

      // next 100
      streamIter.take(100).size shouldBe 100

      // finally complete the logger
      log.complete(0)

      streamIter.size should be > 200
    }
  }
}
