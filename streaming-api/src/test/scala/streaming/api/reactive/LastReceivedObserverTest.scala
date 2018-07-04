package streaming.api.reactive

import monix.reactive.{Observable, Pipe}
import org.scalatest.GivenWhenThen
import streaming.api.BaseStreamingApiSpec
import streaming.api.reactive.LastReceivedObserver.LastElementException

class LastReceivedObserverTest extends BaseStreamingApiSpec with GivenWhenThen {

  "LastReceivedObserver.recover" should {
    "allow observables in error to recover" in {

      Given("An observable which will throw an error ")
      val erroringObservable = Observable.fromIterable(1 to 100).map {
        case i if i % 3 == 0 => sys.error(s"I don't like numbers like $i")
        case i => i
      }

      When("We subscribe w/ a LastReceivedObserver")
      val (singleSub, singlePub) = Pipe.replay[Int].unicast
      erroringObservable.subscribe(LastReceivedObserver[Int](singleSub))

      Then("We can use e.g. onErrorRecoverWith to grab the last message in order to recover")
      val recovering = singlePub.onErrorRecoverWith {
        case LastElementException(last: Int, _) =>
          Observable(last, last + 1, last + 2).map(10000 + _)
      }

      recovering.toListL.runSyncUnsafe(testTimeout) shouldBe List(1, 2, 10002, 10003, 10004)
    }
  }

}
