package jabroni.domain

import jabroni.rest.BaseSpec

import concurrent.Future
import concurrent.duration._

class IteratorPublisherTest extends BaseSpec {

  "IteratorPublisher" should {
    "publish the elements in an iterator" in {
      var tookCounter = 0

      def newIter = Iterator.from(1).take(100).map { x =>
        tookCounter = tookCounter + 1
        x
      }

      val positiveInts = new IteratorPublisher(() => newIter)
      val subscriber = new IterableSubscriber[Int](initialRequestToTake = 1)(pollTimeout = 1.second)

      // check 'hasNext' before subscribing
      val initialHnFuture = Future(subscriber.iterator.hasNext)
      initialHnFuture.isCompleted shouldBe false

      // call the method under test
      positiveInts.subscribe(subscriber)

      subscriber.iterator.next shouldBe 1

      subscriber.iterator.next shouldBe 2
      tookCounter shouldBe 3 // initially 3 as the 'hasNext' look-ahead from the subscriber + the read from the publisher
      subscriber.iterator.next shouldBe 3
      subscriber.iterator.next shouldBe 4


      //... and on and on...
      tookCounter < 10 shouldBe true
    }
  }

}
