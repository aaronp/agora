package agora.flow

import agora.flow.impl.{DurableProcessorInstance, DurableSubscription}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.concurrent.Eventually

class DurableProcessorTest extends BaseFlowSpec with Eventually {

  implicit def asRichListSubscriber[T](subscriber: ListSubscriber[T]) = new {
    def verifyReceived(expected: String*) = {
      eventually {
        subscriber.receivedInOrderReceived() shouldBe expected.toList
      }
    }
  }

  "DurableProcessor as subscriber" should {

    "notify onComplete independently for each subscriber" in {
      val processorUnderTest: DurableProcessorInstance[String] = DurableProcessor[String]()
      processorUnderTest.onNext("first")
      processorUnderTest.onNext("penultimate")

      val sub1 = new ListSubscriber[String]
      processorUnderTest.subscribe(sub1)
      sub1.receivedInOrderReceived() shouldBe Nil
      sub1.request(2)
      sub1.isCompleted() shouldBe false
      eventually {
        sub1.receivedInOrderReceived() shouldBe List("first", "penultimate")
      }

      val sub2 = new ListSubscriber[String]
      processorUnderTest.subscribe(sub2)
      sub2.request(100)
      eventually {
        sub2.receivedInOrderReceived() shouldBe List("first", "penultimate")
        sub2.isCompleted() shouldBe false
      }

      //      processorUnderTest.snapshot().subscribers.size shouldBe 2

      // add one more element and call the method under test
      processorUnderTest.onNext("last")
      processorUnderTest.onComplete()

      // sub1 has still only requested 2
      sub1.isCompleted() shouldBe false
      eventually {
        sub1.receivedInOrderReceived() shouldBe List("first", "penultimate")

        sub2.receivedInOrderReceived() shouldBe List("first", "penultimate", "last")
        sub2.isCompleted() shouldBe true
      }
      //      processorUnderTest.snapshot().subscribers.size shouldBe 1

      sub1.request(1)
      eventually {
        sub1.isCompleted() shouldBe true
        sub1.receivedInOrderReceived() shouldBe List("first", "penultimate", "last")
      }
      //      processorUnderTest.snapshot().subscribers.size shouldBe 0
    }
    "notify onComplete for subscriptions created after it has been completed" in {
      val processorUnderTest = DurableProcessor[String]()
      processorUnderTest.onNext("only element")
      processorUnderTest.onComplete()

      val sub1 = new ListSubscriber[String]
      processorUnderTest.subscribe(sub1)
      sub1.receivedInOrderReceived() shouldBe Nil
      sub1.isCompleted() shouldBe false

      sub1.request(1)
      eventually {
        sub1.isCompleted() shouldBe true
        sub1.receivedInOrderReceived() shouldBe List("only element")
      }

      val sub2 = new ListSubscriber[String]
      sub2.request(2)
      processorUnderTest.subscribe(sub2)
      eventually {
        sub2.receivedInOrderReceived() shouldBe List("only element")
        sub2.isCompleted() shouldBe true
      }
    }

    "publish to subscribers" in {
      val controlMessagePublisher = DurableProcessor[String]()
      val listener                = new ListSubscriber[String]
      var inlineSubscriberMsg     = ""
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
      val processorUnderTest = DurableProcessor[Int]()
      Pub.verifyRequestsPropagated(processorUnderTest)
    }
    "pull from its publisher when its subscribers pull" in {
      object Pub extends TestPub
      val processorUnderTest = DurableProcessor[Int]()
      Pub.verifySubscriber(processorUnderTest)
      processorUnderTest.processorSubscription().size shouldBe 1
    }
  }
  "DurableProcessor.subscribeFrom" should {
    "notify completed when subscribing to indices after the completed index" in {
      val processorUnderTest = DurableProcessor[Int]()
      processorUnderTest.onNext(1)
      processorUnderTest.onComplete()

      val sub = new ListSubscriber[Int]
      sub.request(10)
      processorUnderTest.subscribeFrom(1, sub)
      eventually {
        sub.isCompleted() shouldBe true
      }
      eventually {
        sub.receivedInOrderReceived() shouldBe Nil
      }

      val sub2 = new ListSubscriber[Int]
      sub2.request(10)
      processorUnderTest.subscribeFrom(0, sub2)
      eventually {
        sub2.isCompleted() shouldBe true
        sub2.receivedInOrderReceived() shouldBe List(1)
      }
    }
    "request data before values are pushed" in {
      val processor = DurableProcessor[String]()

      val subscriber = new ListSubscriber[String]
      processor.subscribe(subscriber)

      subscriber.request(7)

      //      val List(only) = processor.snapshot().subscribers.values.toList
      //      only.currentlyRequested shouldBe 7
    }
    "Allow subscriptions to receive already published values" in {
      val processor = DurableProcessor[String]()
      //      processor.firstIndex shouldBe 0
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
  "DurableProcessor.subscribe" should {

    "support multiple subscriptions" in {
      val processor = DurableProcessor[String]()

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
      val processor = DurableProcessor[String]()
      //      processor.firstIndex shouldBe 0
      processor.latestIndex shouldBe None

      processor.onNext("first value")
      eventually {
        processor.latestIndex shouldBe Option(0)
      }

      processor.onNext("second value")
      eventually {
        processor.latestIndex shouldBe Option(1)
      }

      processor.onNext("third value")
      processor.firstIndex shouldBe -1
      eventually {
        processor.latestIndex shouldBe Option(2)
      }

      val subscriber = new ListSubscriber[String]
      processor.subscribe(subscriber)
      subscriber.isSubscribed() shouldBe true
      val subscription = subscriber.subscription().asInstanceOf[DurableSubscription[_]]
      //      subscription.lastRequestedIndex() shouldBe -1
      //      subscription.nextIndexToRequest.get shouldBe -1

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
      eventually {
        subscriber.isCompleted shouldBe true
      }
    }
  }

  class TestPub extends Publisher[Int] with Subscription {
    var subscriber: Subscriber[_ >: Int] = null
    var requests: List[Long]             = Nil

    override def subscribe(s: Subscriber[_ >: Int]): Unit = {
      subscriber = s
      s.onSubscribe(this)
    }

    override def cancel(): Unit = ???

    override def request(n: Long): Unit = synchronized {
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
      eventually {
        if (requests.size == 2) {
          requests shouldBe List(2, 12)
        } else {
          requests shouldBe List(14)
        }
      }
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
        eventually {
          requests shouldBe List(2)
        }

        // this should not result in the processorUnderTest requesting
        downstreamSecond.request(1)
        eventually {
          requests shouldBe List(2)
        }
        downstreamSecond.request(1)
        eventually {
          requests shouldBe List(2)
        }

        // it should now
        downstreamSecond.request(1)
        eventually {
          requests shouldBe List(1, 2)
        }

        downstreamSecond.request(10)
        eventually {
          requests shouldBe List(10, 1, 2)
        }

        // current requested is now 13, downstreamFirst was asking for 2 before
        downstreamFirst.request(8) // now at 10
        // ... so this should be unchanged
        eventually {
          requests shouldBe List(10, 1, 2)
        }

        downstreamFirst.request(5) // now at 15, 15 - 13 is 2
        eventually {
          requests shouldBe List(2, 10, 1, 2)
        }
      }
    }
  }

}
