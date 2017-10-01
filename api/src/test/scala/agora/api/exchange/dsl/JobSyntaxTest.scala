package agora.api.exchange.dsl

import agora.BaseSpec
import agora.api.exchange._
import org.scalatest.concurrent.Eventually

import agora.api.Implicits._
import io.circe.generic.auto._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class JobSyntaxTest extends BaseSpec with Eventually {

  implicit val excecCtxt = scala.concurrent.ExecutionContext.fromExecutorService(daemonicExecutor)

  "Exchange.enqueue" should {
    "return the first result to complete when SelectFirst selection mode is specified" in {
      val exchange = ServerSideExchange()

      var matches = List[MatchNotification]()
      exchange.observer.alwaysWhen {
        case mtch => matches = mtch :: matches
      }

      def createWorker(port: Int) = {
        exchange.subscribe(WorkSubscription.localhost(port), 3).futureValue
      }

      // create 3 workers
      createWorker(1234)
      createWorker(2345)
      createWorker(3456)

      val dispatchCalls = ListBuffer[Dispatch[String]]()
      implicit val testClient = AsClient.async[String, String] { dispatch =>
        dispatchCalls.synchronized {
          dispatchCalls += dispatch
        }
        // have each 'dispatch' call take a different amount of time.
        // in a distributed scenario, the 'AsClient' would be a client of some RPC or Rest call.
        // here we just pretent to sent the request somewhere by waiting some time before completing
        val delay = dispatch.location.port match {
          case 1234 => 50.millis
          case 2345 => 300.millis
          case 3456 => 350.millis
        }
        Thread.sleep(delay.toMillis)

        s"${dispatch.location.port} done"
      }

      // call the method under test
      val result =
        exchange.enqueue("compute something expensive", SubmissionDetails(matchMode = SelectionMode.first()))

      // verify the results:
      // ensure we send requests to all our 3 matched workers
      // ensure there was 1 match in the exchange
      // ensure the response from the rest which took the least amount of time to complete was returned
      eventually {
        dispatchCalls.synchronized {
          dispatchCalls.size shouldBe 3
        }
      }

      matches.size shouldBe 1
      withClue("there should have been 3 candidates") {
        matches.head.chosen.size shouldBe 3
      }
      dispatchCalls.foreach { call =>
        call.matchDetails.remainingItems shouldBe 2
      }
      result.futureValue shouldBe "1234 done"

    }
    "return error when SelectAll selection mode is specified" in {

      val exchange = ServerSideExchange()

      implicit val testClient = AsClient.identity[String]

      val err = intercept[Exception] {
        exchange.enqueue("this should error", SubmissionDetails(matchMode = SelectionAll)).block
      }

      err.getMessage should startWith(
        "An invalid selection mode for this should error was given as part of the " +
          "submission details, as select-all  may return multiple results")
    }
    "return error when SelectN selection mode is specified" in {

      implicit val testClient = AsClient.identity[String]
      val exchange            = ServerSideExchange()

      val err = intercept[Exception] {
        exchange.enqueue("this should error", SubmissionDetails(matchMode = SelectionMode(3))).block
      }

      err.getMessage should startWith(
        "An invalid selection mode for this should error was given as part of the " +
          "submission details, as select-3  may return multiple results")
    }
  }
}
