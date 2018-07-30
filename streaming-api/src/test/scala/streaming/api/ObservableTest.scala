package streaming.api

import monix.eval.Task
import monix.execution.{Ack, CancelableFuture}
import monix.reactive._
import monix.reactive.subjects.Var
import org.scalatest.GivenWhenThen
import streaming.api.reactive.LastReceivedObserver

import scala.concurrent.Await

class ObservableTest extends BaseStreamingApiSpec with GivenWhenThen {

  "delayOnNextBySelector" should {
    "complete when the selector triggers" in {
      val selector = Var(0)

      val x = Observable(1,2,3).delayOnNextBySelector { i =>
        println(i)
        def ok(selNr : Int) = {
          val r = selNr >= i
          println(s"$selNr vs $i --> $r")
          r
        }
        selector.filter(ok).dump(s"Matched $i")
        Observable(1)
      }

      val fut: CancelableFuture[Unit] =  x.doOnNext(value => println(s"got next value $value")).foreach { p =>
        println(s"got " + p)
      }

      println("setting a")
      selector := 1
      println("setting b")
      selector := 2
      println("setting c")
      selector := 3
      selector := 4
      selector := 5
      Await.result(fut, testTimeout)
      println("done")

    }
  }
  "joining stream" ignore {

    /**
      *
      *   Stream 1     Stream 2
      *
      *       +-----+-----+
      *
      *    A  +----->
      *             +-----V
      *                   |
      *    B  +----->     |
      *                   |
      *    C  +----->     |
      *             <-----+  // at this point we're ready to consume the next from Stream 1, which is C
      *             +-----+
      *                   |
      *                   |
      *             <-----+
      *
      * If we get inputs from stream 1 w/ elements [A,B,C] and we're flatMapping stream 2,
      *
      * In this scenario we want to 'discardWhileBusy' from Stream 1, only keeping the latest
      * value.
      *
      * This means that while we're computing the result for A, we then get B, which gets replaced
      * by C
      **/
    "join on the latest tick" in {

      // our base stream through which we'll send A,B and C
      val stream1 = Var("")

      Given("Stream 1 which will drop old events while it is busy")
      val onlyLatestStream1 = {

        // this is the code under test -- prove we drop all but the latest values while busy
        stream1.filter(_ != "") //.merge(OverflowStrategy.BackPressure(1))
      }

      And("A stream two which we can control how long it takes to compute its results")
      // let's manually trigger the computations for the second stream
      val elementATrigger = Var("")
      val elementCTrigger = Var("")
      var stream2Inputs   = List[String]()

      def computeStream2(input: String): Observable[(String, String)] = {
        println(s"Computing stream 2 for $input")
        synchronized {
          stream2Inputs = input :: stream2Inputs
        }
        //        val trigger = firstElemTrigger.filter(_ != "").dump("stream2 trigger")
        //        Observable(s"computed $input").delaySubscriptionWith(trigger)
        val res: Observable[(String, String)] = Observable(input -> s"computed $input")


        res.delayOnNextBySelector {
          case ("A", _) =>
            elementATrigger.filter(_ != "").dump("stream2 trigger for A")
          case ("B", _) =>
            println("Got B ????")
            Observable.raiseError(new Exception("Our test shouldn't receive B as it should wait for A to complete"))
          case ("C", _) => elementCTrigger.filter(_ != "").dump("stream2 trigger for C")
          case other =>
            println(s"Got $other ????")
            sys.error(s"our test data shouldn't only sent 'A', 'B', or 'C', not '${other}'")
        }
      }

      When("We flatMap stream1 w/ stream2")

      val results = onlyLatestStream1.dump("onlyLatestStream1").bufferIntrospective(1).dump("onlyLatestStream1 (buffered)").mapTask { buffer: List[String] =>
        println(s"buffer size is " + buffer.size)

        val List(input) = buffer
        println(s"computing for $input")
        val result: Observable[(String, String)] = computeStream2(input)

        val task: Task[(String, String)] = result.dump(s"Stream2 result from $input").consumeWith(Consumer.head)
        task
      }

//      val results: Observable[(String, String)] = onlyLatestStream1.dump("onlyLatestStream1").mergeMap { input =>
//        println(s"computing for $input")
//        val result: Observable[(String, String)] = computeStream2(input)
//        result.dump(s"Stream2 result from $input")
//      }(BackPressure(2))
//

      //^^^^^^^^^^^^^^^^
      // ============================================================================================================
      // THIS IS THE SECRET SAUCE UNDER TEST -- specify back pressure of 1, which means we won't pull the next 'A'
      // until we're done computing B from stream2
      // ============================================================================================================

//      val results: Observable[(String, String)] = onlyLatestStream1.dump("onlyLatestStream1").flatMapLatest { input =>
//        println(s"computing for $input")
//        val result: Observable[(String, String)] = computeStream2(input)
//        result.dump(s"Stream2 result from $input")
//      }

      And("Observe those flatMapped results")
      // let's observe the output
      results.foreach { result => println(" GOT: " + result)
      }

      And("element 'A' gets pushed through stream1")
      stream1 := "A"

      Then("stream2 should start computing its result")
      eventually {
        stream2Inputs shouldBe List("A")
      }

      When("the next element 'B' gets pushed through stream1")
      stream1 := "B"

      And("stream2 hasn't yet finished computing its result for element 'A'")
      Then("stream2 should NOT receive element B")
      Thread.sleep(testNegativeTimeout.toMillis)
      eventually {
        stream2Inputs shouldBe List("A")
      }

      When("another element 'C' gets pushed through stream1")
      stream1 := "C"

      And("stream2 then finishes computing its result for element 'A'")
      elementATrigger := "go for it!"

      Then("stream2 SHOULD start computing its result for element C")
      eventually {
        stream2Inputs shouldBe List("C", "A")
      }
    }
    "compute using the latest tick, cancelling the previous inputs" ignore {

      // this test outouts e.g.

//      0: onlyLatestStream1 --> A
//      computing for A
//      Computing stream 2 for A
//      1: onlyLatestStream1 --> B
//      computing for B
//      Computing stream 2 for B
//      Got B ????
//      1: stream2 trigger for A canceled
//        1: Stream2 result from A canceled
//      2: onlyLatestStream1 --> C
//      computing for C
//      Computing stream 2 for C
//      1: stream2 trigger for B canceled
//        1: Stream2 result from B canceled

      // our base stream through which we'll send A,B and C
      val stream1 = Var("")

      Given("Stream 1 which will drop old events while it is busy")
      val onlyLatestStream1 = {
        def droppedFromStreamOne(nrDropped: Long): Option[String] = {
          println(s"stream 1 dropping $nrDropped")
          None
        }

        // this is the code under test -- prove we drop all but the latest values while busy
        stream1.filter(_ != "").whileBusyBuffer(OverflowStrategy.DropOldAndSignal(2, droppedFromStreamOne))
      }

      And("A stream two which we can control how long it takes to compute its results")
      // let's manually trigger the computations for the second stream
      val elementATrigger = Var("")
      val elementBTrigger = Var("")
      val elementCTrigger = Var("")
      val elementDTrigger = Var("")
      var stream2Inputs   = List[String]()
      def computeStream2(input: String): Observable[(String, String)] = {
        println(s"Computing stream 2 for $input")
        synchronized {
          stream2Inputs = input :: stream2Inputs
        }
//        val trigger = firstElemTrigger.filter(_ != "").dump("stream2 trigger")
//        Observable(s"computed $input").delaySubscriptionWith(trigger)
        Observable(input -> s"computed $input").delayOnNextBySelector {
          case ("A", _) => elementATrigger.filter(_ != "").dump("stream2 trigger for A")
          case ("B", _) =>
            println("Got B ????")
//            Observable.raiseError(new Exception("Our test shouldn't receive B as it should wait for A to complete"))
            elementBTrigger.filter(_ != "").dump("stream2 trigger for B")
          case ("C", _) => elementCTrigger.filter(_ != "").dump("stream2 trigger for C")
          case ("D", _) => elementDTrigger.filter(_ != "").dump("stream2 trigger for D")
          case other =>
            println(s"Got $other ????")
            sys.error(s"our test data shouldn't only sent 'A', 'B', or 'C', not '${other}'")
        }
      }

      When("We flatMap stream1 w/ stream2")
      val results: Observable[(String, String)] = onlyLatestStream1.dump("onlyLatestStream1").flatMapLatest { input =>
        println(s"computing for $input")
        val result: Observable[(String, String)] = computeStream2(input)
        result.dump(s"Stream2 result from $input")
      }

      And("Observe those flatMapped results")
      // let's observe the output
      results.foreach { result => println(" GOT: " + result)
      }

      And("element 'A' gets pushed through stream1")
      stream1 := "A"

      Then("stream2 should start computing its result")
      eventually {
        stream2Inputs shouldBe List("A")
      }

      When("the next element 'B' gets pushed through stream1")
      stream1 := "B"
      stream1 := "C"

      And("stream2 hasn't yet finished computing its result for element 'A'")
      Then("stream2 should NOT receive element B")
      Thread.sleep(testNegativeTimeout.toMillis)
      eventually {
        stream2Inputs shouldBe List("A")
      }

      When("another element 'D' gets pushed through stream1")
      stream1 := "D"

      And("stream2 then finishes computing its result for element 'A'")
      elementATrigger := "go for it!"

      Then("stream2 SHOULD start computing its result for element D")
      eventually {
        stream2Inputs shouldBe List("D", "A")
      }
    }
  }
  "Var := " ignore {
    "notify when set" in {
      val strVar = Var[String](null)
      strVar := "test"
      val res = strVar.take(1).toListL.runSyncUnsafe(testTimeout)
      res shouldBe List("test")
    }
  }

  "Consumer.loadBalance" ignore {
    "load balance" in {
      val updates = new java.util.concurrent.ConcurrentHashMap[Int, List[String]]()
      val workers: Seq[Consumer.Sync[String, Unit]] = (0 to 3).map { i =>
        Consumer.foreach[String] { in =>
          val text = s"$i got: $in"
          val list = text :: updates.getOrDefault(i, Nil)
          println(text)
          updates.put(i, list)
        }
      }
      val lb                    = Consumer.loadBalance[String, Unit](workers: _*)
      val res: Task[List[Unit]] = lb(Observable("a", "b", "c", "d", "e", "f"))

      import scala.collection.JavaConverters._
      val actual = res.runSyncUnsafe(testTimeout).size
      actual shouldBe workers.size
      updates.asScala.toMap.values.flatten.size shouldBe 6
    }
  }

  "Observables.merge, switch and interleve, scan" ignore {}

  "Observables.flatten" ignore {

    "produce all the elements from the various observables" in {
      val list = Observable(Observable(1, 2), Observable(3, 4), Observable(5, 6)).flatten.toListL.runSyncUnsafe(testTimeout)
      list should contain allOf (1, 2, 3, 4, 5, 6)
    }

  }
  "Observables.toList on a take restricted observable" ignore {
    "take two" in {

      val two: List[Int] = Observable(1, 2, 3, 4).take(2).toListL.runSyncUnsafe(testTimeout)
      two shouldBe List(1, 2)
    }
  }
  "Observables.conflate" ignore {

    "conflate messages while the consumer is busy" in {

      val (from, pipeTo: Observable[String]) = Pipe.replay[String].multicast

      def pimp[T](prefix: String, obs: Observable[T]): Observable[T] = {
        obs
          .doOnNext { n => println(s"  [$prefix.onNext] : $n")
          }
          .doOnError { e => println(s"  [$prefix.onError] : $e")
          }
          .doOnComplete { () => println(s"  [$prefix.onComplete] ")
          }
          .doOnEarlyStop { () => println(s"  [$prefix.onEarluStop] ")
          }
          .doOnNextAck {
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
        val aa                                  = firstList.runSyncUnsafe(testTimeout * 10)
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
      val cnt         = onlyLatest.doOnNext(println).countL
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
