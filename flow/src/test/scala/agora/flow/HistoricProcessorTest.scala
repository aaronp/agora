package agora.flow

import agora.BaseIOSpec
import agora.flow.HistoricProcessor.HistoricSubscription
import cats.instances.int._
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.concurrent.Eventually

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits._

class HistoricProcessorTest extends BaseIOSpec with Eventually {

  implicit def asRichListSubscriber[T](subscriber: ListSubscriber[T]) = new {
    def verifyReceived(expected: String*) = {
      eventually {
        subscriber.receivedInOrderReceived() shouldBe expected.toList
      }
    }
  }

  "HistoricProcessor semigroup" should {
    "request when subscribed if it has subscribers who requested data" in {
      object Pub extends TestPub
      val processorUnderTest = HistoricProcessor.conflate[Int]()
      Pub.verifyRequestsPropagated(processorUnderTest)
    }
    "notify new subscribers of previous values" in {
      val processor = HistoricProcessor.conflate[Int](Option(7))

      processor.onNext(1)

      val first = new ListSubscriber[Int]
      processor.subscribe(first)
      first.received() shouldBe Nil

      first.request(1)
      first.received() shouldBe List(8)

      val second = new ListSubscriber[Int]
      second.request(1)
      processor.subscribe(second)
      second.received() shouldBe List(8)

      processor.onNext(10)
      processor.onNext(100)
      second.received() shouldBe List(8)
      second.request(1)
      second.received() shouldBe List(118, 8)

      processor.onNext(1000)
      first.received() shouldBe List(8)
      first.request(1)
      first.received() shouldBe List(1118, 8)

    }

    "pull from its publisher when its subscribers pull" in {
      object Pub extends TestPub
      import cats.instances.int._
      val processorUnderTest = HistoricProcessor.conflate[Int]()
      Pub.verifySubscriber(processorUnderTest)
    }
  }
  "HistoricProcessor as subscriber" should {

    "publish to subscribers" in {
      val controlMessagePublisher = HistoricProcessor[String]()
      val listener = new ListSubscriber[String]
      var inlineSubscriberMsg = ""
      val listener2 = BaseSubscriber[String](1) {
        case (s, msg) =>
          inlineSubscriberMsg = msg
          s.request(1)
      }
      controlMessagePublisher.subscribe(listener)
      controlMessagePublisher.subscribe(listener2)
      listener.request(1)
      controlMessagePublisher.onNext("first msg")

      eventually {
        listener.received() shouldBe List("first msg")
        inlineSubscriberMsg shouldBe "first msg"
      }
    }
    "request when subscribed if it has subscribers who requested data" in {
      object Pub extends TestPub
      val processorUnderTest = HistoricProcessor[Int]()
      Pub.verifyRequestsPropagated(processorUnderTest)
    }
    "pull from its publisher when its subscribers pull" in {
      object Pub extends TestPub
      val processorUnderTest = HistoricProcessor[Int]()
      Pub.verifySubscriber(processorUnderTest)
      processorUnderTest.processorSubscription().size shouldBe 1
    }
  }
  "HistoricProcessor.subscribeFrom" should {
    "request data before values are pushed" in {
      val processor = HistoricProcessor[String]()

      val subscriber = new ListSubscriber[String]
      processor.subscribe(subscriber)

      subscriber.request(7)

      val List(only) = processor.snapshot().subscribers.values.toList
      only.currentlyRequested shouldBe 7
    }
    "Allow subscriptions to receive already published values" in {
      val processor = HistoricProcessor[String]()
      processor.firstIndex shouldBe 0
      processor.latestIndex shouldBe None

      val startAtThree = new ListSubscriber[String]
      startAtThree.request(2)
      processor.subscribeFrom(2, startAtThree)

      processor.onNext("a") // 0
      processor.onNext("b") // 1
      startAtThree.receivedInOrderReceived() shouldBe Nil

      processor.onNext("c") // 2
      startAtThree.verifyReceived("c")

      val startAtTwo = new ListSubscriber[String]
      startAtTwo.request(1)
      processor.subscribeFrom(1, startAtTwo)
      startAtTwo.verifyReceived("b")
    }
  }
  "HistoricProcessor.subscribe" should {

    "support multiple subscriptions" in {
      val processor = HistoricProcessor[String]()

      val firstSubscriber = new ListSubscriber[String]
      firstSubscriber.request(2)
      processor.subscribe(firstSubscriber)

      processor.onNext("a")
      firstSubscriber.verifyReceived("a")

      val secondSubscriber = new ListSubscriber[String]
      processor.subscribe(secondSubscriber)
      secondSubscriber.receivedInOrderReceived() shouldBe Nil

      processor.onNext("b")
      firstSubscriber.verifyReceived("a", "b")
      secondSubscriber.verifyReceived()

      secondSubscriber.request(1)

      firstSubscriber.verifyReceived("a", "b")
      secondSubscriber.verifyReceived("a")

      processor.onNext("c")
      firstSubscriber.verifyReceived("a", "b")
      secondSubscriber.verifyReceived("a")

      secondSubscriber.request(1)
      firstSubscriber.verifyReceived("a", "b")
      secondSubscriber.verifyReceived("a", "b")

      firstSubscriber.request(2)
      firstSubscriber.verifyReceived("a", "b", "c")
      secondSubscriber.verifyReceived("a", "b")

    }
    "Allow subscriptions to receive already published values" in {
      val processor = HistoricProcessor[String]()
      processor.firstIndex shouldBe 0
      processor.latestIndex shouldBe None

      processor.onNext("first value")
      processor.latestIndex shouldBe Option(0)

      processor.onNext("second value")
      processor.latestIndex shouldBe Option(1)

      processor.onNext("third value")
      processor.firstIndex shouldBe 0
      processor.latestIndex shouldBe Option(2)

      val subscriber = new ListSubscriber[String]
      processor.subscribe(subscriber)
      subscriber.isSubscribed() shouldBe true
      val subscription = subscriber.subscription().asInstanceOf[HistoricSubscription[_]]
      subscription.lastRequestedIndexCounter.get shouldBe -1
      subscription.nextIndexToRequest.get shouldBe -1

      subscriber.received() shouldBe empty
      subscriber.request(2)

      subscriber.verifyReceived("first value", "second value")

      withClue("requesting past the end of the available elements should result in an element being pushed as soon as it's available") {
        subscriber.request(2)
        processor.latestIndex shouldBe Option(2)
        subscriber.verifyReceived("first value", "second value", "third value")

        processor.onNext("last value")
        subscriber.verifyReceived("first value", "second value", "third value", "last value")
      }

      subscriber.isCompleted shouldBe false
      processor.onComplete()
      subscriber.isCompleted shouldBe true
    }
  }

  class TestPub extends Publisher[Int] with Subscription {
    var subscriber: Subscriber[_ >: Int] = null
    var requests: List[Long] = Nil

    override def subscribe(s: Subscriber[_ >: Int]): Unit = {
      subscriber = s
      s.onSubscribe(this)
    }

    override def cancel(): Unit = ???

    override def request(n: Long): Unit = {
      requests = n :: requests
    }

    def verifyRequestsPropagated(processorUnderTest: Publisher[Int] with Subscriber[Int]) = {
      requests shouldBe Nil

      val downstreamFirst = new ListSubscriber[Int]()
      processorUnderTest.subscribe(downstreamFirst)
      downstreamFirst.request(12)
      val downstreamSecond = new ListSubscriber[Int]()
      processorUnderTest.subscribe(downstreamSecond)
      downstreamSecond.request(14)

      subscribe(processorUnderTest)
      requests shouldBe List(14)
    }

    def verifySubscriber(processorUnderTest: Publisher[Int] with Subscriber[Int]) = {
      subscribe(processorUnderTest)
      requests shouldBe Nil

      val downstreamFirst = new ListSubscriber[Int]()
      processorUnderTest.subscribe(downstreamFirst)
      val downstreamSecond = new ListSubscriber[Int]()
      processorUnderTest.subscribe(downstreamSecond)

      withClue("The historic processor should only request the maximum requested of its subscribers") {
        downstreamFirst.request(2)
        requests shouldBe List(2)

        // this should not result in the processorUnderTest requesting
        downstreamSecond.request(1)
        requests shouldBe List(2)
        downstreamSecond.request(1)
        requests shouldBe List(2)

        // it should now
        downstreamSecond.request(1)
        requests shouldBe List(1, 2)

        downstreamSecond.request(10)
        requests shouldBe List(10, 1, 2)

        // current requested is now 13, downstreamFirst was asking for 2 before
        downstreamFirst.request(8) // now at 10
        // ... so this should be unchanged
        requests shouldBe List(10, 1, 2)

        downstreamFirst.request(5) // now at 15, 15 - 13 is 2
        requests shouldBe List(2, 10, 1, 2)
      }
    }
  }

}
