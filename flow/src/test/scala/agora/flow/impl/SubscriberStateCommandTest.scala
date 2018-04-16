package agora.flow.impl

import java.util

import agora.flow.BaseFlowSpec

import scala.concurrent.Promise

class SubscriberStateCommandTest extends BaseFlowSpec {

  "SubscriberStateCommand.conflate" should {
    "not collapse OnCancel commands" in {
      val original = mkQueue(OnCancel, OnCancel)

      val List((OnCancel, p1), (OnCancel, p2)) = conflate(original)
      p1.isCompleted shouldBe false
      p2.isCompleted shouldBe false
    }
    "treat multiple OnComplete commands as an error" in {
      val original = mkQueue(OnComplete(1), OnComplete(1))

      val actual = conflate(original)
      val List((OnError(err), promise)) = actual

      val promises: List[SubscriberStateCommandResult] = original.take(2).map(_._2.future.futureValue)
      val List(StopResult(err1), StopResult(err2)) = promises
      println(err)
      println(err1)
      println(err2)
    }

    "collapse OnRequest(n) commands" in {
      val original = mkQueue(
        OnRequest(1),
        OnRequest(2),
        OnRequest(3))

      val List((OnRequest(6), promise)) = conflate(original)

      promise.isCompleted shouldBe false
      original.take(2).map(_._2.future.futureValue) shouldBe List(ContinueResult, ContinueResult)
    }
    "collapse OnNewIndexAvailable(n) commands" in {

      val original = mkQueue(
        OnNewIndexAvailable(1),
        OnNewIndexAvailable(2),
        OnNewIndexAvailable(3))

      val List((OnNewIndexAvailable(3), promise)) = conflate(original)

      promise.isCompleted shouldBe false
      original.take(2).map(_._2.future.futureValue) shouldBe List(ContinueResult, ContinueResult)
    }

  }

  def mkQueue(cmds: SubscriberStateCommand*): List[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])] = {
    cmds.map(_ -> Promise[SubscriberStateCommandResult]()).toList
  }

  def conflate(cmds: List[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])]): List[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])] = {
    val q = SubscriberState.newQ(cmds.size)
    cmds.foreach(q.add)
    val jQueue = SubscriberStateCommand.conflate(q)

    import scala.collection.JavaConverters._
    val list = new util.ArrayList[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])]()
    jQueue.drainTo(list)
    list.asScala.toList
  }
}
