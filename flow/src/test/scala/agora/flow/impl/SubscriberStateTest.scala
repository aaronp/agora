package agora.flow.impl

import agora.flow.{BaseFlowSpec, DurableProcessorReader, ListSubscriber}

import scala.util.{Success, Try}

class SubscriberStateTest extends BaseFlowSpec {

  "SubscriberState.update" should {
    "not push elements when none are available" in {
      val subscriber = new ListSubscriber[String]
      val state      = new SubscriberState(subscriber, TestDao, -1)
      state.update(OnRequest(1)) shouldBe ContinueResult
      subscriber.received() shouldBe Nil
      subscriber.isCompleted() shouldBe false
    }
    "not push available elements until requested" in {
      val subscriber = new ListSubscriber[String]
      val state      = new SubscriberState(subscriber, TestDao, -1)
      state.update(OnNewIndexAvailable(4)) shouldBe ContinueResult
      subscriber.receivedInOrderReceived() shouldBe Nil
      subscriber.isCompleted() shouldBe false

      state.update(OnRequest(1)) shouldBe ContinueResult
      subscriber.receivedInOrderReceived() shouldBe List("0")
    }
    "push all elements when told some are available after requested" in {
      val subscriber = new ListSubscriber[String]
      val state      = new SubscriberState(subscriber, TestDao, -1)
      state.update(OnRequest(2)) shouldBe ContinueResult
      state.update(OnNewIndexAvailable(4)) shouldBe ContinueResult
      subscriber.receivedInOrderReceived() shouldBe List("0", "1")
      subscriber.isCompleted() shouldBe false

      state.update(OnRequest(1)) shouldBe ContinueResult
      subscriber.receivedInOrderReceived() shouldBe List("0", "1", "2")
    }
    "push all elements when completed" in {
      val subscriber = new ListSubscriber[String]
      val state      = new SubscriberState(subscriber, TestDao, -1)
      state.update(OnRequest(1)) shouldBe ContinueResult
      state.update(OnComplete(1)) shouldBe ContinueResult
      subscriber.receivedInOrderReceived() shouldBe List("0")
      subscriber.isCompleted() shouldBe false

      state.update(OnRequest(1)) shouldBe StopResult(None)
      subscriber.receivedInOrderReceived() shouldBe List("0", "1")
      subscriber.isCompleted() shouldBe true
    }
    "return Stop when cancelled" in {
      val state = new SubscriberState(new ListSubscriber[String], TestDao, -1)
      state.update(OnCancel) shouldBe CancelResult
    }
    "return Stop when an exception is thrown" in {
      val errorSubscriber = new ListSubscriber[String] {
        override def onNext(t: String): Unit = {
          sys.error(t)
        }
      }
      val state = new SubscriberState(errorSubscriber, TestDao, -1)
      state.update(OnRequest(1)) shouldBe ContinueResult
      val StopResult(Some(err)) = state.update(OnNewIndexAvailable(1))
      err.getMessage shouldBe "0"
    }
  }

  object TestDao extends DurableProcessorReader[String] {
    override def at(index: Long): Try[String] = Success("" + index)
  }

}
