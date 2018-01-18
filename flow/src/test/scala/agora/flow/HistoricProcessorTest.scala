package agora.flow

import agora.BaseIOSpec
import org.scalatest.concurrent.Eventually

import scala.concurrent.ExecutionContext.Implicits._

class HistoricProcessorTest extends BaseIOSpec with Eventually {

  implicit def asRichListSubscriber[T](subscriber: ListSubscriber[T]) = new {
    def verifyReceived(expected: String*) = {
      eventually {
        subscriber.receivedInOrderReceived() shouldBe expected.toList
      }
    }
  }

  "HistoricProcessor.subscribeFrom" should {
    "Allow subscriptions to receive already published values" in {
      val processor = HistoricProcessor[String](HistoricProcessorDao())
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
      val processor = HistoricProcessor[String](HistoricProcessorDao())

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
      val processor = HistoricProcessor[String](HistoricProcessorDao())
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
      val subscription = subscriber.subscription().asInstanceOf[processor.HistoricSubscriber]
      subscription.lastRequestedIndex.get shouldBe -1
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
}
