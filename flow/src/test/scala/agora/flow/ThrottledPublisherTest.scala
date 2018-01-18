package agora.flow

class ThrottledPublisherTest extends BaseFlowSpec {

  "ThrottledPublisher.allowRequested" should {
    "error if subscribed to multiple times" in {
      val pub = BaseProcessor.withMaxCapacity[String](10)

      // the class under test
      val throttled = new ThrottledPublisher(pub)

      throttled.subscribe(new ListSubscriber[String])
      val err = intercept[Exception] {
        throttled.subscribe(new ListSubscriber[String])
      }

      err.getMessage should include("A throttled publisher is intended for a single use")
    }
    "only request as many as are allowed" in {
      val pub = BaseProcessor.withMaxCapacity[String](10)

      // the class under test
      val throttled = new ThrottledPublisher(pub)
      throttled.allowRequested(1) shouldBe Long.MinValue

      val listSub = new ListSubscriber[String]
      throttled.subscribe(listSub)

      (0 to 10).foreach(i => pub.publish(s"value-$i"))

      withClue("we shouldn't receive any events until the throttle lets us") {
        listSub.received() shouldBe Nil
        listSub.request(3)
        listSub.received() shouldBe Nil
      }

      // call the method under test
      withClue("we allow 0 and the user subscription has requested 3, so none should yet be sent") {
        throttled.allowRequested(1) shouldBe 1L
        listSub.received() shouldBe List("value-0")
        throttled.allowed() shouldBe 0L
      }

      withClue("we allow 5, but the user so far has only requested 3, so we should receive 3 of the published 10") {
        throttled.allowRequested(5) shouldBe 2L
        listSub.received() shouldBe List("value-2", "value-1", "value-0")
        throttled.allowed() shouldBe 3l
      }

      withClue("we should have 2 left of our allowed 5") {
        listSub.clear()
        listSub.request(1)
        listSub.received() shouldBe List("value-3")
        throttled.allowed() shouldBe 2l

        listSub.request(1)
        listSub.received() shouldBe List("value-4", "value-3")
        throttled.allowed() shouldBe 1l

        listSub.request(1)
        listSub.received() shouldBe List("value-5", "value-4", "value-3")
        throttled.allowed() shouldBe 0l

        listSub.request(1)
        listSub.received() shouldBe List("value-5", "value-4", "value-3")
        throttled.allowed() shouldBe 0l

        throttled.allowRequested(1) shouldBe 1L
        listSub.received() shouldBe List("value-6", "value-5", "value-4", "value-3")
      }
    }
  }
}
