package agora.api.exchange

import agora.BaseSpec
import agora.api.Implicits
import agora.api.json.JMatcher
import agora.api.worker.{HostLocation, WorkerDetails, WorkerRedirectCoords}
import io.circe.generic.auto._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.{postfixOps, reflectiveCalls}

trait ExchangeSpec extends BaseSpec with Eventually with Implicits {

  import ExchangeSpec._

  /**
    * @return true when our exchange under test can support match observes (true for server-side, false for clients currently)
    */
  def supportsObserverNotifications = true

  def newExchange(observer: MatchObserver): Exchange

  def exchangeName = getClass.getSimpleName.filter(_.isLetter).replaceAllLiterally("Test", "")

  s"$exchangeName.submit" should {

    def newSubscription(name: String) =
      WorkSubscription.forDetails(WorkerDetails(HostLocation.localhost(1234))).append("name", name)

    "match against orElse clauses if the original match criteria doesn't match" in {

      val job = "some job".asJob
        .matching("topic" === "primary")
        .orElse("topic" === "secondary")
        .orElse("topic" === "tertiary")
        .withId("someJobId")
      val anotherJob =
        "another job".asJob.matching(JMatcher.matchNone).withId("anotherId").withAwaitMatch(false)

      val primary =
        newSubscription("primary").append("topic", "primary").withSubscriptionKey("primary key")
      val tertiary =
        newSubscription("tertiary").append("topic", "tertiary").withSubscriptionKey("tertiary key")

      // verify some preconditions
      withClue("precondition proof that the subscriptions match the jobs as expected") {
        job.matches(primary) shouldBe true
        job.matches(tertiary) shouldBe false
        job.orElseSubmission.get.matches(tertiary) shouldBe false
        job.orElseSubmission.get.orElseSubmission.get.matches(tertiary) shouldBe true
      }

      // create our exchange
      val obs                = MatchObserver()
      val exchange: Exchange = newExchange(obs)

      // and set some
      var notifications = ListBuffer[MatchNotification]()
      obs.alwaysWhen {
        case notification =>
          notifications += notification
      }

      // set up our subscriptions
      exchange.subscribe(primary).futureValue shouldBe WorkSubscriptionAck("primary key")
      // don't actually request any work items
      exchange.subscribe(tertiary).futureValue shouldBe WorkSubscriptionAck("tertiary key")
      exchange.take(tertiary.key.get, 3).futureValue shouldBe RequestWorkAck("tertiary key", 0, 3)

      // submit the job which won't match anything
      exchange.submit(anotherJob).futureValue shouldBe SubmitJobResponse(anotherJob.jobId.get)

      // submit our 'fallback' job. For tests w/ local exchanges (e.g. ones where 'supportsObserverNotifications' is
      // true), The 'awaitMatch' has no effect, and we use the observerr directly. For remote cases (where
      // 'supportsObserverNotifications' is false), we can block and case the response future as a BlockingSubmitJobResponse
      val BlockingSubmitJobResponse(_, _, _, workerCoords, workerDetails) =
        if (supportsObserverNotifications) {
          // await our match...
          val matchFuture = obs.onJob(job)

          // submit the job
          val submitResponse = exchange.submit(job).futureValue
          submitResponse shouldBe SubmitJobResponse(job.jobId.get)

          matchFuture.futureValue
        } else {
          val submitResponse = exchange.submit(job.withAwaitMatch(true)).futureValue
          submitResponse.asInstanceOf[BlockingSubmitJobResponse]
        }

      workerCoords should contain only (WorkerRedirectCoords(HostLocation("localhost", 1234), "tertiary key", 2))
      workerDetails should contain only (tertiary.details)

      val QueueStateResponse(actualJobs, actualSubscriptions) = exchange.queueState().futureValue

      withClue("the queue state should now only contain the unmatched job") {
        actualJobs should contain only (anotherJob)
      }
      actualSubscriptions should contain allOf (PendingSubscription(primary.key.get, primary, 0),
      PendingSubscription(tertiary.key.get, tertiary, 2))

      // we should've matched tertiary
      if (supportsObserverNotifications) {
        notifications should contain only (MatchNotification("someJobId",
                                                             job,
                                                             List(Candidate(tertiary.key.get, tertiary, 2))))
      }
    }
  }
  exchangeName should {
    "be able to cancel subscriptions" in {
      val obs          = MatchObserver()
      val ex: Exchange = newExchange(obs)

      val subscription: WorkSubscriptionAck =
        ex.subscribe(WorkSubscription(HostLocation.localhost(1234))).futureValue

      // check out queue
      val subscriptions = ex.queueState().futureValue.subscriptions.map(_.key)
      subscriptions should contain only (subscription.id)

      // call the method under test
      ex.cancelSubscriptions(subscription.id, "unknown").futureValue.canceledSubscriptions shouldBe Map(
        subscription.id -> true,
        "unknown"       -> false)

      // check out queue
      val afterCancel = ex.queueState().futureValue.subscriptions
      afterCancel should be(empty)
    }
    "be able to cancel jobs" in {
      val obs          = MatchObserver()
      val ex: Exchange = newExchange(obs)

      val input = DoubleMe(11).asJob.withAwaitMatch(false)
      input.jobId should be(empty)

      val jobId = ex.submit(input).futureValue.asInstanceOf[SubmitJobResponse].id

      // check out queue
      val queuedJobs = ex.queueState().futureValue.jobs.flatMap(_.jobId)
      queuedJobs should contain only (jobId)

      // call the method under test
      ex.cancelJobs(jobId, "unknownJob").futureValue.canceledJobs shouldBe Map(jobId -> true, "unknownJob" -> false)

      // check out queue
      val afterCancel = ex.queueState().futureValue.jobs
      afterCancel should be(empty)
    }

    if (supportsObserverNotifications) {
      "match jobs with work subscriptions" in {

        object obs extends MatchObserver
        var matches = List[MatchNotification]()
        obs.alwaysWhen {
          case jobMatch => matches = jobMatch :: matches
        }
        val ex: Exchange = newExchange(obs)

        val jobId = ex
          .submit(DoubleMe(11).asJob.withAwaitMatch(false))
          .futureValue
          .asInstanceOf[SubmitJobResponse]
          .id
        val jobPath = ("value" gt 7) and ("value" lt 17)

        val sub = WorkSubscription(HostLocation.localhost(1234), jobMatcher = jobPath)

        val subscriptionId = ex.subscribe(sub).futureValue.id
        ex.take(subscriptionId, 1).futureValue shouldBe RequestWorkAck(subscriptionId, 0, 0)

        matches.size shouldBe 1
      }
    }
  }

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(3, Seconds)), interval = scaled(Span(150, Millis)))

}

object ExchangeSpec {

  case class DoubleMe(value: Int)

}
