package streaming.api

import monix.execution.CancelableFuture
import monix.reactive._
import streaming.api.reactive.LastReceivedObserver

import scala.concurrent.Await
import scala.concurrent.duration._

class ObservableTest extends BaseStreamingApiSpec {

  "Observable.toList" should {

    "return a list when complete" in {

      val someStream = Observable("first", "second", "last")
      val x: Observable[String] = someStream.
        doOnNext { x =>
          println(s"\n\nServer send us: $x \n\n")
          //endpoint.toRemote.onComplete()
        }.
        doOnComplete(() => println("we're complete")).
        doOnError(println)

      x.foreach { frame =>
        println("yet again, client got " + frame)
        //adminResults = frame :: (if (adminResults == null) Nil else adminResults)
      }

      val asList: CancelableFuture[List[String]] = x.toListL.runAsync
      val list = Await.result(asList, 10.seconds)
      println("LISTY: " + list)
    }
  }
  "Observables.chat" ignore {

    "" in {

      val x: (Observer[Observable[String]], Observable[Observable[String]]) = Pipe.replay[Observable[String]].multicast
      val (from, to: Observable[Observable[String]]) = x

      val y = to.whileBusyBuffer(OverflowStrategy.Unbounded).concat

    }
  }
  "Observables.recover" ignore {
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
