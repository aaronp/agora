package agora.rest.streams

import agora.BaseSpec
import agora.io.IterableSubscriber
import agora.rest.HasMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.reactivestreams.Subscription

import scala.concurrent.Future
import scala.concurrent.duration._

class IterableSubscriberTest extends BaseSpec with HasMaterializer {

  class TestSubscription extends Subscription {
    var requested = List[Long]()

    override def cancel(): Unit = ???

    override def request(l: Long): Unit = {
      requested = l :: requested
    }
  }

  "IterableSubscriber.iterate" should {

    val longLine = "longLine".padTo(500, '.')
    val expected =
      s"""Here is some text
         |It doesn't
         |even have to be anything special
         |it does contain a
         |${longLine}
         |however
        """.stripMargin

    "iterate a source of bytes" in {
      def lines = IterableSubscriber.iterate(Source.single(ByteString(expected)), longLine.length, true)

      lines.mkString("\n") shouldBe expected
      lines.size shouldBe expected.lines.size
    }
  }
  "IterableSubscriber.iterator" should {
    "block on .next until there is an element in the queue" in {
      val is           = new IterableSubscriber[Int](3)(3.seconds)
      val subscription = new TestSubscription
      is.onSubscribe(subscription)
      val firstNextCallFuture = Future(is.iterator.next())
      subscription.requested shouldBe (List(3))
      firstNextCallFuture.isCompleted shouldBe false

      is.onNext(123)
      firstNextCallFuture.futureValue shouldBe 123
      subscription.requested shouldBe (List(1, 3))
    }
    "accept several onNext elements and iterate them in order" in {
      val is           = new IterableSubscriber[Int]()(3.seconds)
      val subscription = new TestSubscription
      is.onSubscribe(subscription)

      (0 to 4).foreach(is.onNext)
      // note ... we DON'T call isComplete, but are just reading
      // from the iterator as many elements as 'onNext' as offered
      is.iterator.take(5).toList shouldBe (0 to 4).toList
      subscription.requested shouldBe (List(1, 1, 1, 1, 1, is.initialRequestToTake))
    }
    "consume the whole subscription after 'isComplete' is called" in {
      val is           = new IterableSubscriber[Int]()(3.seconds)
      val subscription = new TestSubscription
      is.onSubscribe(subscription)

      (0 to 4).foreach(is.onNext)
      is.onComplete
      is.iterator.toList shouldBe (0 to 4).toList
      subscription.requested shouldBe (List(1, 1, 1, 1, 1, is.initialRequestToTake))
    }
  }
}
