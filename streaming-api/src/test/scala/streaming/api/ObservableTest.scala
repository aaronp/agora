package streaming.api

import monix.eval.Task
import monix.reactive._
import streaming.api.reactive.LastReceivedObserver

class ObservableTest extends BaseStreamingApiSpec {

  "Observables.merge, switch and interleve, scan" ignore {

  }
  "Observables.toList on a take restricted observable" should {
    "take two" in {

      val two: List[Int] = Observable(1, 2, 3, 4).take(2).toListL.runSyncUnsafe(testTimeout)
      two shouldBe List(1, 2)
    }
  }
  "Observables.conflate" ignore {

    "conflate messages while the consumer is busy" in {

      val (from, pipeTo: Observable[String]) = Pipe.replay[String].multicast

      def pimp[T](prefix: String, obs: Observable[T]): Observable[T] = {
        obs.doOnNext { n =>
          println(s"  [$prefix.onNext] : $n")
        }.doOnError { e =>
          println(s"  [$prefix.onError] : $e")
        }.doOnComplete { () =>
          println(s"  [$prefix.onComplete] ")
        }.doOnEarlyStop { () =>
          println(s"  [$prefix.onEarluStop] ")
        }.doOnNextAck {
          case (n, ack) =>
            println(s"  [$prefix.onNextAck($n, $ack)] ")
        }
      }

      val to = pimp("to", pipeTo)


      val invocations = pimp("foldLeft", to.foldLeftF(List[String]()) {
        case (list, string) =>
          println(s"appending $string to $list")
          string :: list
      })
      val onlyLatest = pimp("shared", pimp("whileBusy", invocations.whileBusyBuffer(OverflowStrategy.DropOld(2))).share)

      def push(n: String) = {
        from.onNext(n).map { x =>
          println(s"pushed $n")

          x
        }
      }

      val firstBatchF = for {
        _ <- push("first")
        _ <- push("second")
        _ <- push("third")
        _ <- push("fourth")
        _ <- push("fifth")
        _ <- push("sixth")
        x <- push("seventh")

      } yield {
        x
      }

      println("Just taking one...")

      def consume(n: Int) = {
        val firstList: Task[List[List[String]]] = pimp(s"take($n)", onlyLatest.take(n)).toListL
        val aa = firstList.runSyncUnsafe(testTimeout * 10)
        println(s"consuming $n produces: " + aa.flatten)
      }

      consume(3)

      println("pushing a bunch more..")
      val doneF = (0 to 10).foldLeft(firstBatchF) {
        case (fut, n) => fut.flatMap(_ => push(s"plus $n"))
      }

      consume(2)

      //      println("Just taking three...")
      //      val three = onlyLatest.take(3).toListL.runSyncUnsafe(testTimeout).flatten
      //      println("Just taking three..." + three)

      println("completeing...")
      doneF.onComplete(_ => from.onComplete())
      val cnt = onlyLatest.doOnNext(println).countL
      val total: Long = cnt.runSyncUnsafe(testTimeout)
      println(total)
      if (total > 3) {
        fail()
      }
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
