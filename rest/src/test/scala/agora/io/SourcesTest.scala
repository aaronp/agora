package agora.io

import java.util.concurrent.atomic.AtomicInteger

import agora.api.BaseSpec
import agora.rest.HasMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.Eventually

class SourcesTest extends BaseSpec with HasMaterializer with Eventually {

  import Sources._

  "Source.onComplete" should {
    "Invoke the given function after a source " in {
      @volatile var callbackInvoked = 0

      def iter = new Iterator[Int] {
        val lastCall = new AtomicInteger(0)
        override def hasNext: Boolean = {
          val hn = (lastCall.get < 10)
          if (hn) {
            callbackInvoked shouldBe 0
          }
          hn
        }

        override def next(): Int = lastCall.incrementAndGet()
      }

      @volatile var lastSeen = 0
      val src = Source.fromIterator(() => iter).onComplete {
        lastSeen shouldBe 10
        callbackInvoked = callbackInvoked + 1
      }

      src.runForeach { elm =>
        lastSeen = elm
      }.futureValue

      callbackInvoked shouldBe 1
    }
    "Invoke the given function when a source completes" in {
      @volatile var calls = 0
      val src = Source.empty.onComplete {
        calls = calls + 1
      }
      calls shouldBe 0

      // run once, check our callback is invoked
      src.runWith(Sink.ignore).futureValue
      calls shouldBe 1

      // running a second time should call us again
      src.runWith(Sink.ignore).futureValue
      calls shouldBe 2
    }
  }
}
