package agora.flow

class BaseProcessorTest extends BaseFlowSpec {

  "BaseProcessor" should {
    "request more work when any of its subscriptions request work" in {
      val a = BaseProcessor.withMaxCapacity[String](10)
      val b = BaseProcessor.withMaxCapacity[String](10)
      val c = BaseProcessor.withMaxCapacity[String](10)

      // wire in a -> b -> c
      val aList = new ListSubscriber[String]
      a.subscribe(b)
      a.subscribe(aList)

      val bList = new ListSubscriber[String]
      b.subscribe(bList)
      b.subscribe(c)

      val cList = new ListSubscriber[String]
      c.subscribe(cList)

      // verify setup - nothing should be requesting anything
      a.subscriptionCount() shouldBe 2
      b.subscriptionCount() shouldBe 2
      c.subscriptionCount() shouldBe 1
      a.currentRequestedCount() shouldBe 0
      b.currentRequestedCount() shouldBe 0
      c.currentRequestedCount() shouldBe 0

      //
      // REQUEST WORK
      //

      // have c's subscription request some data ...
      withClue("a request from c should cascade up to a") {
        cList.request(1)
        a.currentRequestedCount() shouldBe 1
        b.currentRequestedCount() shouldBe 1
        c.currentRequestedCount() shouldBe 1
      }

      // have b's 1st subscription request some data ...
      withClue("a request from b should cascade up to a") {
        b.request(1)
        a.currentRequestedCount() shouldBe 2
        b.currentRequestedCount() shouldBe 1
        c.currentRequestedCount() shouldBe 1
      }

      // have b's 2nd subscription (bList) request some data ... it should NOT take more, as both subscriptions
      // are each requesting just 1 work item, so we shouldn't then have the parent request 2
      withClue("a request from a second b subscription should not increase the subscription count") {
        bList.request(1)
        a.currentRequestedCount() shouldBe 2
        b.currentRequestedCount() shouldBe 1
        c.currentRequestedCount() shouldBe 1
      }

      // have b's 2nd subscription (bList) request some data ... it should NOT take more, as both subscriptions
      // are each requesting just 1 work item, so we shouldn't then have the parent request 2
      withClue("a SECOND request from a b subscription should now increase the subscription count") {
        bList.request(1)
        a.currentRequestedCount() shouldBe 3
        b.currentRequestedCount() shouldBe 2
        c.currentRequestedCount() shouldBe 1
      }

      //
      // PUBLISH DATA
      //

      // now push some data through
      a.onNext("first")

      withClue("onNext should decrement the requested count") {
        a.currentRequestedCount() shouldBe 2
        b.currentRequestedCount() shouldBe 1
        c.currentRequestedCount() shouldBe 0
      }

      aList.received() shouldBe Nil
      bList.received() shouldBe List("first")
      cList.received() shouldBe List("first")

      a.onNext("second")

      withClue("onNext should decrement the requested count") {
        a.currentRequestedCount() shouldBe 1
        b.currentRequestedCount() shouldBe 0
        c.currentRequestedCount() shouldBe 0
      }

      aList.received() shouldBe Nil
      bList.received() shouldBe List("second", "first")
      cList.received() shouldBe List("first")

      a.onNext("third")
      aList.received() shouldBe Nil
      bList.received() shouldBe List("second", "first")
      cList.received() shouldBe List("first")

      cList.request(2)
      cList.received() shouldBe List("third", "second", "first")

    }
  }

}
