package streaming.api

import monix.reactive._
import streaming.api.reactive.LastReceivedObserver

class ObservableTest extends BaseStreamingApiSpec {

  "Observables.recover" should {
    "allow observables in error to recover" in {

      case class LastElementException[T](last: T, err: Throwable) extends Throwable
      val base = {
        val ints: Observable[Int] = Observable(1, 2, 3)
        ints.map {
          case i: Int =>
            if (i % 3 == 0) {
              sys.error(s"I don't like numbers like $i")
            }
            i
        }
      }

      val (singleSub, singlePub) = Pipe.publishToOne[Int].unicast

      base.subscribe(LastReceivedObserver(singleSub))

      singlePub.bufferIntrospective(1)
      val recovering = singlePub.onErrorRecoverWith {
        case LastElementException(last: Int, _) =>
          Observable(last, last + 1, last + 2)
      }

      val list: List[Int] = recovering.toListL.runSyncUnsafe(testTimeout)
      println(list)
    }
  }

}
