package agora.rest.exchange

import java.util.concurrent.{CountDownLatch, TimeUnit, TimeoutException}

import agora.api.exchange._
import agora.api.exchange.observer.ExchangeObserver
import agora.rest.HasMaterializer

import scala.compat.Platform
import scala.concurrent.Await
import scala.concurrent.duration._

class ActorExchangeTest extends ExchangeSpec with HasMaterializer {

  override def newExchange(observer: ExchangeObserver): Exchange = {
    val ex = Exchange(observer)(JobPredicate())
    ActorExchange(ex, testTimeout, system)
  }

  "ActorExchange.withQueueState" should {
    "buffer incoming messages while performing the supplied thunk" in {
      val exchangeUnderTest = ActorExchange(Exchange.instance(), bufferTimeoutThreshold = 1.minute, system)

      val firstJob = "buffered job".asJob.withAwaitMatch(false).withId("first job id")

      /**
        * first, confirm normal connectivity... send a messages and get an ack,
        * checking how long they take
        */
      val ackTime = {
        val started  = Platform.currentTime
        val firstAck = exchangeUnderTest.submit(firstJob).futureValue
        Platform.currentTime - started
      }

      val thunkCompleteLatch = new CountDownLatch(1)
      val thunkArriveLatch   = new CountDownLatch(1)

      /**
        * Now do something within the queue state and prove other messages are being buffered
        */
      val resultFuture = exchangeUnderTest.withQueueState() { state =>
        // block 'til we're in the thunk...
        thunkArriveLatch.countDown()
        // now block within the thunk...
        thunkCompleteLatch.await()
        state
      }

      // ensure we're inside the thunk....
      thunkArriveLatch.await(testTimeout.toMillis, TimeUnit.MILLISECONDS) shouldBe true
      // at this point the pessimistic lock is in place an we're buffering messages

      //... so verify our messages are buffered by submitted a job and waiting twice
      // as long to complete as it took the first time (and so observing that it's buffered)
      val future = exchangeUnderTest.submit("c".asJob.withAwaitMatch(false)).mapTo[SubmitJobResponse]
      Thread.sleep(ackTime * 2)

      withClue("our 'thunk' should have put the actor in a buffering state for all incoming requests") {
        future.isCompleted shouldBe false
      }

      // finally let out thunk complete ... the 'buffered job should now complete'
      thunkCompleteLatch.countDown()
      future.futureValue.id should not be (empty)

      withClue("as the second request was being buffered while our test thunk was working, our test thunk should've only returned the first job") {
        resultFuture.futureValue.jobs shouldBe List(firstJob)
      }
    }
    "stop buffering incoming messages if the supplied doesn't complete in N milliseconds" in {
      val configuredTimeout = 200.millis
      val exchangeUnderTest = ActorExchange(Exchange.instance(), bufferTimeoutThreshold = configuredTimeout, system)

      val thunkCompleteLatch = new CountDownLatch(1)
      val thunkArriveLatch   = new CountDownLatch(1)

      /**
        * Do something in the exchange which won't complete within our configured timeout
        */
      val resultFuture = exchangeUnderTest.withQueueState() { state =>
        // block 'til we're in the thunk...
        thunkArriveLatch.countDown()
        // now block within the thunk...
        thunkCompleteLatch.await()
        state
      }

      // ensure we're inside the thunk....
      thunkArriveLatch.await(testTimeout.toMillis, TimeUnit.MILLISECONDS) shouldBe true
      // at this point the pessimistic lock is in place an we're buffering messages

      //... this message should be initiall buffered, but then released when our thunk doesn't complete
      val future = exchangeUnderTest.submit("delayedJob".asJob.withAwaitMatch(false)).mapTo[SubmitJobResponse]
      future.futureValue.id should not be (empty)

      withClue("Our exclusive thunk job should've been failed") {
        val timeoutExp = intercept[TimeoutException] {
          Await.result(resultFuture, testTimeout)
        }
        timeoutExp.getMessage should startWith("We started handling a 'WithQueueState' message at about 20")
        timeoutExp.getMessage should endWith(", but have now timed out after 200 milliseconds.")
      }
    }
  }

}
