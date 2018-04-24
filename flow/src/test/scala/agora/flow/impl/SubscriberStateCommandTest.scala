package agora.flow.impl

import java.util

import agora.flow.BaseFlowSpec

import scala.concurrent.Promise

class SubscriberStateCommandTest extends BaseFlowSpec {

  "SubscriberStateCommand.conflate" should {
    "collapse OnCancel commands" in {
      val original = mkQueue(OnCancel, OnCancel)

      val List((OnCancel, p1)) = conflate(original)
      p1.isCompleted shouldBe false

      val secondPromise = original.last._2.future
      secondPromise should not be (p1)
      secondPromise.isCompleted shouldBe true
      secondPromise.futureValue shouldBe ContinueResult
    }
    "treat multiple OnComplete commands as an error" in {
      val original = mkQueue(OnComplete(1), OnComplete(1))

      val actual                        = conflate(original)
      val List((OnError(err), promise)) = actual

      val promises: List[SubscriberStateCommandResult]                 = original.take(2).map(_._2.future.futureValue)
      val List(StopResult(Some(stopErr1)), StopResult(Some(stopErr2))) = promises
      err.getMessage shouldBe "Encountered multiple 'onComplete' messages while conflating the queue"
      stopErr1.getMessage shouldBe "Encountered multiple 'onComplete' messages while conflating the queue"
      stopErr2.getMessage shouldBe "Encountered multiple 'onComplete' messages while conflating the queue"
    }

    "collapse OnRequest(n) commands whos sum is greater than Long.MaxValue" in {
      val original = mkQueue(OnRequest(Long.MaxValue - 10), OnRequest(Long.MaxValue / 2), OnRequest(5))

      val List((OnRequest(total), promise)) = conflate(original)

      promise.isCompleted shouldBe false
      total shouldBe Long.MaxValue
      original.take(2).map(_._2.future.futureValue) shouldBe List(ContinueResult, ContinueResult)
    }
    "collapse OnRequest(n) commands" in {
      val original = mkQueue(OnRequest(1), OnRequest(2), OnRequest(3))

      val List((OnRequest(6), promise)) = conflate(original)

      promise.isCompleted shouldBe false
      original.take(2).map(_._2.future.futureValue) shouldBe List(ContinueResult, ContinueResult)
    }
    "collapse OnNewIndexAvailable(n) commands" in {

      val original = mkQueue(OnNewIndexAvailable(1), OnNewIndexAvailable(2), OnNewIndexAvailable(3))

      val List((OnNewIndexAvailable(3), promise)) = conflate(original)

      promise.isCompleted shouldBe false
      original.take(2).map(_._2.future.futureValue) shouldBe List(ContinueResult, ContinueResult)
    }

  }

  /**
    * The test cases dealing w/ how to conflate OnError messages are kept separate
    * for now, as I've not decided on behaviour for them. The simple case for now
    * is just to keep all errors, though I can see ideally just keeping the first.
    */
  // TODO - conflate errors .... maybe
  "SubscriberStateCommand.conflate errors" ignore {

    "EITHER keep all OnError(err) commands" in {
      val original = mkQueue(OnError(new Exception("first bang")), OnError(new Exception("second bang")))

      val errors = conflate(original)

      errors.size shouldBe 2
      original.take(2).map(_._2.future.futureValue) shouldBe List(ContinueResult, ContinueResult)
    }
    "OR only keep the first OnError(err) command and stop after future errors" in {
      val original = mkQueue(OnError(new Exception("first bang")), OnError(new Exception("second bang")))

      val List((OnError(err), promise)) = conflate(original)

      promise.isCompleted shouldBe false
      val List(ContinueResult, StopResult(Some(firstError))) = original.take(2).map(_._2.future.futureValue)
      err.getMessage shouldBe "first bang"
      firstError.getMessage shouldBe "first bang"
    }
  }

  def mkQueue(cmds: SubscriberStateCommand*): List[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])] = {
    cmds.map(_ -> Promise[SubscriberStateCommandResult]()).toList
  }

  def conflate(
      cmds: List[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])]): List[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])] = {
    val q = SubscriberState.newQ(cmds.size)
    cmds.foreach(q.add)
    val jQueue = SubscriberStateCommand.conflate(q)

    import scala.collection.JavaConverters._
    val list = new util.ArrayList[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])]()
    jQueue.drainTo(list)
    list.asScala.toList
  }
}
